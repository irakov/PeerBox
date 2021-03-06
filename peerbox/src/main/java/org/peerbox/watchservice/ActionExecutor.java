package org.peerbox.watchservice;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hive2hive.core.exceptions.AbortModificationCode;
import org.hive2hive.core.exceptions.ErrorCode;
import org.hive2hive.core.exceptions.Hive2HiveException;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.processframework.exceptions.ProcessExecutionException;
import org.peerbox.app.manager.ProcessHandle;
import org.peerbox.app.manager.file.FileExecutionFailedMessage;
import org.peerbox.app.manager.file.IFileManager;
import org.peerbox.events.IMessage;
import org.peerbox.notifications.InformationNotification;
import org.peerbox.presenter.settings.synchronization.FileHelper;
import org.peerbox.presenter.settings.synchronization.messages.FileExecutionStartedMessage;
import org.peerbox.presenter.settings.synchronization.messages.FileExecutionSucceededMessage;
import org.peerbox.view.tray.SynchronizationCompleteNotification;
import org.peerbox.view.tray.SynchronizationErrorsResolvedNotification;
import org.peerbox.view.tray.SynchronizationStartsNotification;
import org.peerbox.watchservice.filetree.IFileTree;
import org.peerbox.watchservice.filetree.composite.FileComponent;
import org.peerbox.watchservice.filetree.composite.FolderComposite;
import org.peerbox.watchservice.states.ExecutionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;

/**
 * The FileActionExecutor service observes a set of file actions in a queue.
 * An action is executed as soon as it is considered to be "stable", i.e. no more events were
 * captured within a certain period of time.
 *
 * @author albrecht
 *
 */
public class ActionExecutor implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);

	/**
	 *  amount of time that an action has to be "stable" in order to be executed
	 */
	public static final long ACTION_WAIT_TIME_MS = 8000;
	public static final int NUMBER_OF_EXECUTE_SLOTS = 10;
	public static final int MAX_EXECUTION_ATTEMPTS = 3;

	private final IFileManager fileManager;
	private final FileEventManager fileEventManager;
	private boolean waitForActionCompletion = true;

	private final BlockingQueue<ExecutionHandle> asyncHandles;
	private final Thread asyncHandlesThread;

	private final Thread executorThread;

	@Inject
	public ActionExecutor(final FileEventManager eventManager, final IFileManager fileManager) {
		this.fileEventManager = eventManager;
		this.fileManager = fileManager;

		asyncHandles = new LinkedBlockingQueue<ExecutionHandle>();
		asyncHandlesThread = new Thread(new ExecutingActionHandler(), "AsyncActionHandlerThread");

		executorThread = new Thread(this, "ActionExecutorThread");

	}

	public void start() {
		executorThread.start();
	}

	public void stop() {
		// TODO: change stop to something that is not deprecated and recommended.
		executorThread.stop();
	}

	@Override
	public void run() {
		asyncHandlesThread.start();
		processActions();
	}

	/**
	 * Processes the action in the action queue, one by one.
	 * @throws IllegalFileLocation
	 * @throws NoPeerConnectionException
	 * @throws NoSessionException
	 */
	private synchronized void processActions() {
		while (true) {

			FileComponent next = null;

			try {

				next = fileEventManager.getFileComponentQueue().take();

				logger.trace("check readyness {}", next.getPath());
				if (!isFileComponentReady(next)) {
					logger.debug("{} is not ready yet!", next.getPath());
					fileEventManager.getFileComponentQueue().remove(next);
					next.getAction().updateTimestamp();
					fileEventManager.getFileComponentQueue().add(next);
					continue;
				}

				if (isTimerReady(next.getAction()) && isExecuteSlotFree()) {

					removeFromDeleted(next);
					removeFromCreated(next);
					removeFromFailed(next.getPath());
					logger.debug("Start execution: {}", next.getPath());

					ExecutionHandle ehandle = next.getAction().execute(fileManager);
					if (waitForActionCompletion) {
						if (ehandle != null && ehandle.getProcessHandle() != null) {
							logger.debug("Put into async handles!");
							FileHelper file = new FileHelper(next.getPath(), next.isFile());
//							fileEventManager.getMessageBus().publish(new FileExecutionStartedMessage(file));
							publishMessage(new FileExecutionStartedMessage(file, next.getAction().getCurrentState().getStateType()));
							asyncHandles.put(ehandle);
						} else {
//							if(!next.isSynchronized()){
//								fileEventManager.getMessageBus().publish(new FileDesyncMessage(next.getPath()));
//							}
						}
						if(asyncHandles.size() != 0){
//							fileEventManager.getMessageBus().publish(new SynchronizationStartsNotification());
							publishMessage(new SynchronizationStartsNotification());
						}
					} else {
						onActionExecuteSucceeded(next.getAction());
					}
				} else {
					if (!isExecuteSlotFree()) {
						logger.debug("All slots used! Current jobs: ");
						logRunningJobs();
					}
					fileEventManager.getFileComponentQueue().add(next);
					long timeToWait = calculateWaitTime(next);
					wait(timeToWait);
				}

			} catch (InterruptedException iex) {
				logger.error("Exception occurred: {}", iex.getMessage(), iex);
			} catch (NoSessionException nse) {
				logger.warn("No session - cannot execute pending actions.", nse);
			} catch(NoPeerConnectionException npc) {
				logger.warn("No peer connection - cannot execute pending actions.", npc);
			} catch (Exception e) {
				logger.error("Exception occurred: {}", e.getMessage(), e);
				// action FAILED!! --> exception occurred during execute()
			}
		}
	}

	private boolean isFileComponentReady(FileComponent next) {
		return next.isReady();
	}

	/**
	 * Checks whether an action is ready to be executed
	 * @param action Action to be executed
	 * @return true if ready to be executed, false otherwise
	 */
	private boolean isTimerReady(IAction action) {
		long ageMs = getActionAge(action);
		return ageMs >= ACTION_WAIT_TIME_MS;
	}

	/**
	 * Computes the age of an action
	 * @param action
	 * @return age in ms
	 */
	private long getActionAge(IAction action) {
		return System.currentTimeMillis() - action.getTimestamp();
	}

	private long calculateWaitTime(FileComponent action) {
		long timeToWait = ACTION_WAIT_TIME_MS - getActionAge(action.getAction()) + 1L;
		if (timeToWait < 500L) {
			// wait at least some time
			timeToWait = 500L;
		}
		return timeToWait;
	}

	private boolean isExecuteSlotFree() {
		return asyncHandles.size() < NUMBER_OF_EXECUTE_SLOTS;
	}

	public void setWaitForActionCompletion(boolean wait) {
		this.waitForActionCompletion = wait;
	}

	public BlockingQueue<ExecutionHandle> getFailedJobs() {
		return asyncHandles;
	}

	/**
	 * @return the file tree
	 */
	private IFileTree getFileTree() {
		return fileEventManager.getFileTree();
	}

	private void logRunningJobs() {
		Iterator<ExecutionHandle> it = asyncHandles.iterator();
		int index = 0;
		while (it.hasNext()) {
			ExecutionHandle next = it.next();
			IAction tmpAction = next.getAction();
			logger.trace("[{}] {} - {}", index,
					tmpAction.getCurrentStateName(),
					tmpAction.getFile().getPath());
			++index;
		}
	}

	private void removeFromDeleted(FileComponent next) {
		SetMultimap<String, FileComponent> deletedByContent = getFileTree().getDeletedByContentHash();
		SetMultimap<String, FolderComposite> deletedByStructure = getFileTree().getDeletedByStructureHash();
		removeComponentFromSetMultimap(next, deletedByContent, deletedByStructure);
	}

	private void removeFromCreated(FileComponent next){
		SetMultimap<String, FileComponent> createdByContent = getFileTree().getCreatedByContentHash();
		SetMultimap<String, FolderComposite> createdByStructure = getFileTree().getCreatedByStructureHash();
		removeComponentFromSetMultimap(next, createdByContent, createdByStructure);
	}

	private void removeFromFailed(Path failedOperation) {
		fileEventManager.getFailedOperations().remove(failedOperation);

		if (fileEventManager.getFailedOperations().size() == 0) {
			publishMessage(new SynchronizationErrorsResolvedNotification());
		}
	}

	private void publishMessage(IMessage message) {
		if (fileEventManager.getMessageBus() != null) {
			fileEventManager.getMessageBus().publish(message);
		}
	}

	private void removeComponentFromSetMultimap(FileComponent toRemove,
			SetMultimap<String, FileComponent> byContent,
			SetMultimap<String, FolderComposite> byStructure){
		Iterator<Map.Entry<String, FileComponent>> componentIterator = byContent.entries().iterator(); //.sameHashes.iterator();

		if(toRemove.isFile()){
			synchronized(byContent){
				while(componentIterator.hasNext()){
					FileComponent candidate = componentIterator.next().getValue();
					if(candidate.getPath().toString().equals(toRemove.getPath().toString())){
						componentIterator.remove();
						break;
					}
					if(System.currentTimeMillis() - candidate.getAction().getTimestamp() > ACTION_WAIT_TIME_MS){
						logger.trace("Remove old entry: {}", candidate.getPath());
						componentIterator.remove();
					}
				}
			}
		} else {
			Iterator<Map.Entry<String, FolderComposite>> folderIterator = byStructure.entries().iterator();
			synchronized(byStructure){
				while(folderIterator.hasNext()){
					Map.Entry<String, FolderComposite> candidate = folderIterator.next();
					if(candidate.getValue().getPath().toString().equals(toRemove.getPath().toString())){
						folderIterator.remove();
						break;
					}
				}
			}
		}
	}

	public void onActionExecuteSucceeded(final IAction action) {
		final FileComponent file = action.getFile();
		logger.debug("Action succeeded: {} {}.",
				file.getPath(), action.getCurrentStateName());

		//inform gui to adjust icon
		FileHelper fileHelper = new FileHelper(file.getPath(), file.isFile());
		publishMessage(new FileExecutionSucceededMessage(fileHelper, action.getCurrentState().getStateType()));

		boolean changedWhileExecuted = action.getChangedWhileExecuted();
		file.setIsUploaded(true);
		action.onSucceeded();

		if (changedWhileExecuted) {
			logger.trace("File: {} changed during the execution process to state {}. "
					+ "Put back into the queue",
					file.getPath(),
					action.getCurrentStateName());
			action.updateTimestamp();
			fileEventManager.getFileComponentQueue().add(file);
		}
	}


	private void handleExecutionError(IAction action, ProcessExecutionException pex) {
		final FileComponent file = action.getFile();
		logger.error("Action failed: {}", file.getPath(), pex);

		action.onFailed();

		boolean errorHandled = false;
		if (pex != null && pex.getCause() != null) {
			if (pex.getCause() instanceof Hive2HiveException) {
				Hive2HiveException h2hex = (Hive2HiveException) pex.getCause();
				if (h2hex.getError() != null) {
					ErrorCode error = h2hex.getError();
					errorHandled = handleErrorByCode(action, error);
				}
			}
		}

		if (!errorHandled) {
			handleErrorDefault(action);
		}

	}


	private void handleErrorDefault(IAction action) {
		final Path path = action.getFile().getPath();
		logger.trace("Default Error Handling: Re-initiate execution - {} - {} - attempt({}).",
				path, action.getCurrentStateName(), action.getExecutionAttempts());

		if (action.getExecutionAttempts() <= MAX_EXECUTION_ATTEMPTS) {
			action.updateTimestamp();
			fileEventManager.getFileComponentQueue().add(action.getFile());
		} else {
			FileHelper file = new FileHelper(path, action.getFile().isFile());
//			fileEventManager.getMessageBus().publish(new FileExecutionFailedMessage(file));
			publishMessage(new FileExecutionFailedMessage(file));
			fileEventManager.getMessageBus().post(new InformationNotification("Synchronization error ",
					"Operation on " + path + " failed")).now();
			logger.error("To many attempts, action of {} has not been executed again.", path);
			onActionExecuteSucceeded(action);
			fileEventManager.getFailedOperations().add(action.getFile().getPath());
		}
	}

	private boolean handleErrorByCode(IAction action, ErrorCode error) {
		final Path path = action.getFile().getPath();

		if (error == AbortModificationCode.SAME_CONTENT) {

			logger.debug("Update of file {} failed as content hash did not change. "
					+ "As there is no action to perform, we consider it as successful.", path);
			
			FileComponent notModified = getFileTree().getFile(path);
			if (notModified == null) {
				logger.trace("FileComponent not found (null): {}", path);
			}
			FileHelper file = new FileHelper(action.getFile().getPath(), action.getFile().isFile());
			publishMessage(new FileExecutionSucceededMessage(file, action.getCurrentState().getStateType()));
			action.onSucceeded();
			return true;

		} else if (error == AbortModificationCode.FOLDER_UPDATE) {

			logger.debug("Attempt to update folder {} failed as folder cannot be updated.", path);
			FileHelper file = new FileHelper(action.getFile().getPath(), action.getFile().isFile());
			publishMessage(new FileExecutionSucceededMessage(file, action.getCurrentState().getStateType()));
			action.onSucceeded();
			return true;

		} else if (error == AbortModificationCode.ROOT_DELETE_ATTEMPT) {

			logger.debug("Attempt to delete the root folder {} failed (operation not allowed)", path);
			return true;

		} else if (error == AbortModificationCode.NO_WRITE_PERM) {

			logger.debug("Attempt to delete or write to {} failed. No write-permissions.", path);
			return true;
		} else if (error == AbortModificationCode.LARGE_FILE_UPDATE){
			logger.debug("File {} is a large file and cannot be updated, as only one "
					+ "version can exist. The update request is discarded and "
					+ "considered as successful.");
			FileHelper file = new FileHelper(action.getFile().getPath(), action.getFile().isFile());
			publishMessage(new FileExecutionSucceededMessage(file, action.getCurrentState().getStateType()));
			action.onSucceeded();
			return true;
		}
//		else if (error == AbortModificationCode.FILE_DOES_NOT_EXIST){
//
//		}

		return false; // error not handled
	}


	private class ExecutingActionHandler implements Runnable {

		@Override
		public void run() {
			processExecutingActions();
		}

		private void processExecutingActions() {
			while(true) {
				try {

					ExecutionHandle next = asyncHandles.take();

					try {

						// check whether there is a process attached
						ProcessHandle<Void> process = next.getProcessHandle();
						if(process != null) {
							process.getFuture().get(5, TimeUnit.SECONDS);
						} else {
							// no async process, i.e. do not need to wait
						}

						// if this point reached, no error occurred (get() did not throw exception)
						onActionExecuteSucceeded(next.getAction());

						if(asyncHandles.size() == 0){
//							fileEventManager.getMessageBus().publish(new SynchronizationCompleteNotification());
							publishMessage(new SynchronizationCompleteNotification());
						}
					} catch (ExecutionException eex) {

						ProcessExecutionException pex = null;
						if (eex.getCause() instanceof ProcessExecutionException) {
							pex = (ProcessExecutionException) eex.getCause();
						}
						handleExecutionError(next.getAction(), pex);

					} catch (CancellationException | InterruptedException e) {
						logger.warn("Exception while getting future result: {}", e.getMessage());
					} catch (TimeoutException tex) {
						logger.debug("Could not get result of failed item, timed out. {}",
								next.getAction().getFile().getPath());
						// add it again and try later
						asyncHandles.put(next);
					}

				} catch (Exception e) {
					logger.warn("Exception in processFailedActions: ", e);
				}
			}
		}

	}
}
