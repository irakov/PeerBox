package org.peerbox.watchservice.states;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.processframework.exceptions.InvalidProcessStateException;
import org.hive2hive.processframework.exceptions.ProcessExecutionException;
import org.peerbox.app.manager.file.IFileManager;
import org.peerbox.presenter.settings.synchronization.FileHelper;
import org.peerbox.watchservice.IAction;
import org.peerbox.watchservice.IFileEventManager;
import org.peerbox.watchservice.conflicthandling.ConflictHandler;
import org.peerbox.watchservice.filetree.composite.FileComponent;
import org.peerbox.watchservice.states.listeners.LocalFileUpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the modify state handles all events which would like
 * to alter the state from Modify to another state (or keep the current state) and decides
 * whether an transition into another state is allowed.
 *
 *
 * @author winzenried
 *
 */
public class LocalUpdateState extends AbstractActionState {

	private final static Logger logger = LoggerFactory.getLogger(LocalUpdateState.class);

	public LocalUpdateState(IAction action) {
		super(action, StateType.LOCAL_UPDATE);
	}
	
	@Override
	public ExecutionHandle execute(IFileManager fileManager) throws NoSessionException, NoPeerConnectionException, InvalidProcessStateException, ProcessExecutionException {
		final Path path = action.getFile().getPath();
		logger.debug("Execute LOCAL UPDATE: {}", path);
		handle = fileManager.update(path);
		if (handle != null && handle.getProcess() != null) {
			FileHelper file = new FileHelper(path, action.getFile().isFile());
			handle.getProcess().attachListener(new LocalFileUpdateListener(file, action.getFileEventManager().getMessageBus()));
			handle.executeAsync();
		} else {
			System.err.println("Process or handle is null.");
		}
		return new ExecutionHandle(action, handle);
	}

	@Override
	public AbstractActionState changeStateOnLocalCreate() {
		logStateTransition(getStateType(), EventType.LOCAL_CREATE, StateType.LOCAL_UPDATE);
		return this;
	}

	@Override
	public AbstractActionState changeStateOnRemoteDelete() {
		logStateTransition(getStateType(), EventType.REMOTE_DELETE, StateType.LOCAL_CREATE);
		return new LocalCreateState(action);
	}

	@Override
	public AbstractActionState changeStateOnRemoteMove(Path oldFilePath) {
		logStateTransition(getStateType(), EventType.REMOTE_MOVE, StateType.LOCAL_UPDATE);
		return this;
	}

	@Override
	public AbstractActionState changeStateOnRemoteCreate() {
		logStateTransition(getStateType(), EventType.REMOTE_CREATE, StateType.REMOTE_CREATE);
		return new RemoteCreateState(action);
	}

	@Override
	public AbstractActionState handleRemoteCreate() {
		ConflictHandler.resolveConflict(action.getFile().getPath());
		updateTimeAndQueue();
		return changeStateOnRemoteCreate();
	}

	@Override
	public AbstractActionState handleRemoteDelete() {
		updateTimeAndQueue();
		return changeStateOnRemoteDelete();
	}

	@Override
	public AbstractActionState handleRemoteUpdate() {
		ConflictHandler.resolveConflict(action.getFile().getPath());
		updateTimeAndQueue();
		return changeStateOnRemoteUpdate();
	}

	//TODO write test-case for this!
	@Override
	public AbstractActionState handleRemoteMove(Path path) {
		//Remove the file from the queue, move it in the tree, put it to the queue, move it on disk.
		Path srcPath = action.getFile().getPath();
		action.getFileEventManager().getFileComponentQueue().remove(action.getFile());
		FileComponent src = action.getFileEventManager().getFileTree().deleteFile(action.getFile().getPath());
		action.getFileEventManager().getFileTree().putFile(path, src);
		updateTimeAndQueue();

		if (Files.exists(srcPath)) {
			try {
				Files.move(srcPath, path);
			} catch (IOException e) {
				logger.warn("Could not move file: from src={} to dst={} ({})",
						srcPath, path, e.getMessage(), e);
			}
		}
		return changeStateOnRemoteMove(path);
	}


}
