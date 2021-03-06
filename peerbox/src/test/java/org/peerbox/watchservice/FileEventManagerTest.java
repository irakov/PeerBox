package org.peerbox.watchservice;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.peerbox.app.manager.file.IFileManager;
import org.peerbox.testutils.FileTestUtils;
import org.peerbox.watchservice.filetree.FileTree;
import org.peerbox.watchservice.filetree.composite.FileComponent;
import org.peerbox.watchservice.states.EstablishedState;
import org.peerbox.watchservice.states.InitialState;
import org.peerbox.watchservice.states.LocalCreateState;
import org.peerbox.watchservice.states.LocalHardDeleteState;
import org.peerbox.watchservice.states.LocalMoveState;
import org.peerbox.watchservice.states.LocalUpdateState;
import org.sql2o.logging.SysOutLogger;

import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

/**
 *
 * @author Claudio
 * This test creates a new directory in the user's home directory, where some files are created for
 * test purposes. The directory and the files are deleted after the execution of the testcases. This class
 * tests if the events triggered by the FolderWatchService are correctly aggregated and delivered to the H2H
 * framework, whereas the FileManager is mocked to decouple the test from the filesharing library
 */
public class FileEventManagerTest {

	private static int nrFiles = 9;
	private static FileTree fileTree;
	private static FileEventManager manager;
	private static ActionExecutor actionExecutor;
	private static IFileManager fileManager;

	private static String parentPath = System.getProperty("user.home") + File.separator + "PeerBox_FileEventManagerTest" + File.separator;
	private static File testDirectory;
	private static ArrayList<String> filePaths = new ArrayList<String>();
	private static ArrayList<File> files = new ArrayList<File>();



	/**
	 * Create the test directory and the files.
	 */
	@BeforeClass
	public static void staticSetup(){
		fileTree = new FileTree(Paths.get(parentPath), null, true);
		manager = new FileEventManager(fileTree, null);
		fileManager = Mockito.mock(IFileManager.class);
		actionExecutor = new ActionExecutor(manager, fileManager);
		actionExecutor.setWaitForActionCompletion(false);
		actionExecutor.start();
	}

	/**
	 * Delete the test directory and the files.
	 */
	@AfterClass
	public static void rollback(){
		for(int i = 0; i < nrFiles; i++){
			files.get(i).delete();
		}
		assertTrue(testDirectory.delete());
	}

	@Before
	public void setup(){
		testDirectory = new File(parentPath);
		testDirectory.mkdir();
		try {
			for(int i = 0; i < nrFiles; i++){
				filePaths.add(parentPath + "file" + i + ".txt");
				files.add(new File(filePaths.get(i)));
				files.get(i).createNewFile();
				System.out.println("Created file " + files.get(i));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Triggers a create event and a modify event on the same file. Ensures
	 * that only one element is stored in the action queue. This element
	 * has to be still in the create state after this two events are processed.
	 */
	@Test
	public void onFileCreatedTest(){
		BlockingQueue<FileComponent> fileComponentsToCheck = manager.getFileComponentQueue().getQueue();

		long start = System.currentTimeMillis();
		System.out.println("Start onFileCreatedTest");
		manager.onLocalFileCreated(Paths.get(filePaths.get(0)));
		assertTrue(fileComponentsToCheck.size() == 1);
		assertTrue(fileComponentsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);

		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		assertTrue(fileComponentsToCheck.size() == 1);
		assertTrue(fileComponentsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);

		FileComponent file = fileComponentsToCheck.peek();
		//check if the testcase was run in time
		long end = System.currentTimeMillis();
		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);

		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);

		assertTrue(file.getAction().getCurrentState() instanceof EstablishedState);
		assertTrue(fileComponentsToCheck.size() == 0);

		//cleanup
		manager.onLocalFileHardDelete(Paths.get(filePaths.get(0)));
		sleepMillis(200); //wait for the state machine before delete is simulated
		manager.onLocalFileDeleted(Paths.get(filePaths.get(0)));
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);
		System.out.println("Current state: " + file.getAction().getCurrentState().getClass());
		assertTrue(manager.getFileTree().getFile(files.get(0).toPath()) == null);
		assertTrue(file.getAction().getCurrentState() instanceof InitialState);
		assertTrue(fileComponentsToCheck.size() == 0);
	}

	/**
	 * This test simulates a create event and waits ActionExecutor.ACTION_WAIT_TIME_MS amount of
	 * time for the event to be handled. After that, a move is simulated using a delete event on
	 * the same file and a create event on a new file with the same content (but different name).
	 */
	@Test
	public void fromDeleteToMoveTest(){
		//handle artificial create event, wait for handling
		manager.onLocalFileCreated(Paths.get(filePaths.get(7)));
		BlockingQueue<FileComponent> actionsToCheck = manager.getFileComponentQueue().getQueue();;
		FileComponent file1 = actionsToCheck.peek();
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);

		//check if exactly one element exists in the queue
		assertTrue(actionsToCheck.size() == 0);

		//initiate delete event
		long start = System.currentTimeMillis();

		manager.onLocalFileDeleted(Paths.get(filePaths.get(7)));
		assertTrue(actionsToCheck.size() == 1);

		//initiate re-creation, ensure that all happens in time
		manager.onLocalFileCreated(Paths.get(filePaths.get(8)));
		FileComponent file2 = actionsToCheck.peek();
		assertTrue(actionsToCheck.size() == 1);
		System.out.println(actionsToCheck.peek().getAction().getCurrentState().getClass());
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalMoveState);

		long end = System.currentTimeMillis();
		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);

		//cleanup
		deleteFile(Paths.get(filePaths.get(8)));
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);
		assertTrue(manager.getFileTree().getFile(files.get(8).toPath()) == null);
		assertTrue(actionsToCheck.size() == 0);
		assertTrue(file1.getAction().getCurrentState() instanceof InitialState);
		assertTrue(file1.getAction().getCurrentState() instanceof InitialState);
	}

	/**
	 * Simulate a file delete and an additional modify event, check if the file
	 * remains in the delete state and only one action is stored in the queue.
	 */
	@Test
	public void onFileDeletedTest(){
		BlockingQueue<FileComponent> actionsToCheck = manager.getFileComponentQueue().getQueue();;
		SetMultimap<String, FileComponent> deletedFiles = manager.getFileTree().getDeletedByContentHash();
		System.out.println("Start onFileDeletedTest");
		manager.onLocalFileCreated(Paths.get(filePaths.get(0)));
		FileComponent createdFile = actionsToCheck.peek();
		//HERE
		assertTrue(actionsToCheck.size() == 1);
		assertTrue(createdFile.getAction().getCurrentState() instanceof LocalCreateState);

		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);

		assertTrue(createdFile.getAction().getCurrentState() instanceof EstablishedState);
		assertTrue(actionsToCheck.size() == 0);

		long start = System.currentTimeMillis();

		manager.onLocalFileHardDelete(Paths.get(filePaths.get(0)));
		sleepMillis(200);
		manager.onLocalFileDeleted(Paths.get(filePaths.get(0)));
		System.out.println(actionsToCheck.size());
		assertTrue(actionsToCheck.size() == 1);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalHardDeleteState);

		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		assertTrue(actionsToCheck.size() == 1);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalHardDeleteState);

		System.out.println("deletedFiles.size(): " + deletedFiles.size());
		//assertTrue(deletedFiles.size() == 1);
		//Set<FileComponent> equalHashes = deletedFiles.get(createdFile.getContentHash());
		//assertTrue(equalHashes.size() == 1);
		//assertTrue(equalHashes.contains(createdFile));

		//check if the testcase was run in time
		long end = System.currentTimeMillis();
		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);



		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 5);

		assertTrue(actionsToCheck.size() == 0);
		assertTrue(manager.getFileTree().getFile(files.get(0).toPath()) == null);
		System.out.println(createdFile.getAction().getCurrentState().getClass());
		assertTrue(createdFile.getAction().getCurrentState() instanceof InitialState);
		System.out.println(actionsToCheck.size());
		assertTrue(deletedFiles.size() == 0);
	}

	/**
	 * This test issues several modify events for the same file over a long
	 * period to check if the events are aggregated accordingly.
	 * @throws IOException
	 */
	@Test
	public void onFileModifiedTest() throws IOException{
		BlockingQueue<FileComponent> actionsToCheck = manager.getFileComponentQueue().getQueue();;

		long start = System.currentTimeMillis();
		System.out.println("Start onFileModifiedTest");
		manager.onLocalFileCreated(Paths.get(filePaths.get(0)));
		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		assertTrue(actionsToCheck.size() == 1);
		assertNotNull(actionsToCheck);
		assertNotNull(actionsToCheck.peek());
		assertNotNull(actionsToCheck.peek().getAction().getCurrentState());
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState); //no null pointers should occur anymore here

		long end = System.currentTimeMillis();

		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);

		//issue continuous modifies over a period longer than the wait time
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);

		FileTestUtils.writeRandomData(files.get(0).toPath(), 50);
		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS / 2);

		FileTestUtils.writeRandomData(files.get(0).toPath(), 50);
		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS / 2);

		FileComponent comp = actionsToCheck.peek();
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalUpdateState);
		assertTrue(actionsToCheck.size() == 1);

		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);
		printBlockingQueue(actionsToCheck);
		assertTrue(actionsToCheck.size() == 0);
	//	System.out.println(comp.getAction().getCurrentState().getClass());

		//cleanup
		manager.onLocalFileHardDelete(Paths.get(filePaths.get(0)));
		sleepMillis(200);
		manager.onLocalFileDeleted(Paths.get(filePaths.get(0)));
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 5);
		assertTrue(manager.getFileTree().getFile(files.get(0).toPath()) == null);
		assertTrue(comp.getAction().getCurrentState() instanceof InitialState);
		assertTrue(actionsToCheck.size() == 0);

	}

	private void printBlockingQueue(BlockingQueue<FileComponent> queue){
		System.out.println("Queue:");
		ArrayList<FileComponent> components = new ArrayList<FileComponent>(queue);
		int i = 0;
		for(FileComponent comp : components){
			System.out.println(i + ": " + comp.getAction().getCurrentState().getClass() + ": " + comp.getPath());
		}
	}

	/**
	 * Advanced test with four files and different events on them.
	 *
	 * The different events:
	 * - modify file0
	 * - create file1
	 * - delete file0
	 * - modify file2
	 * - delete file2
	 * - create file3 > move from file2 to file3
	 *
	 * Expected action queue content
	 *
	 * (tail) [move file2 to file3], [delete file0] [create file1] (head)
	 * @throws IOException
	 */

	@Test
	public void multipleFilesTest() throws IOException{
		//measure start time to ensure the testcase runs before the queue is processed
		System.out.println("Start multipleFilesTest");
		BlockingQueue<FileComponent> actionsToCheck = manager.getFileComponentQueue().getQueue();;

		//issue all the events, check state of head and if the action corresponds to the correct file
		manager.onLocalFileCreated(Paths.get(filePaths.get(0)));
		//manager.onFileCreated(Paths.get(filePaths.get(1)), false);
		manager.onLocalFileCreated(Paths.get(filePaths.get(2)));
		//manager.onFileCreated(Paths.get(filePaths.get(3)), false);
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 2);
		long start = System.currentTimeMillis();
		FileTestUtils.writeRandomData(files.get(0).toPath(), 50);
		manager.onLocalFileModified(Paths.get(filePaths.get(0)));
		sleepMillis(50);
		printQueue(actionsToCheck);
		assertTrue(actionsToCheck.size() == 1);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalUpdateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(0)));

		manager.onLocalFileCreated(Paths.get(filePaths.get(1)));
		sleepMillis(10);
		assertTrue(actionsToCheck.size() == 2);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalUpdateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(0)));


		manager.onLocalFileHardDelete(Paths.get(filePaths.get(0)));
		sleepMillis(200);
		manager.onLocalFileDeleted(Paths.get(filePaths.get(0)));
		sleepMillis(10);
		System.out.println("actionsToCheck.size() " + actionsToCheck.size());
		ArrayList<FileComponent> array = new ArrayList<FileComponent>(actionsToCheck);
		for(FileComponent comp : array){
			System.out.println(comp.getPath() + ": " + comp.getAction().getCurrentState().getClass().toString());
		}
		assertTrue(actionsToCheck.size() == 2);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(1)));


		FileTestUtils.writeRandomData(files.get(2).toPath(), 50);
		manager.onLocalFileModified(Paths.get(filePaths.get(2)));

		sleepMillis(10);
		System.out.println("actionsToCheck.size() " + actionsToCheck.size());
		assertTrue(actionsToCheck.size() == 3);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(1)));
		Files.move(files.get(2), files.get(3));

		manager.onLocalFileDeleted(Paths.get(filePaths.get(2)));
		sleepMillis(10);
		System.out.println("size: " + actionsToCheck.size());
		
		Vector<FileComponent> actions = new Vector<FileComponent>(actionsToCheck);
		for(int i = 0; i < actions.size(); i++){
			System.out.println(i + ": " + actions.get(i).getPath() + " - " + actions.get(i).getAction().getCurrentState().getClass());
		}
		
		assertTrue(actionsToCheck.size() == 3);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(1)));

		manager.onLocalFileCreated(Paths.get(filePaths.get(3)));
		sleepMillis(10);
		System.out.println("actionsToCheck.size() " + actionsToCheck.size());
		assertTrue(actionsToCheck.size() == 3);
		assertTrue(actionsToCheck.peek().getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(actionsToCheck.peek().getPath().toString().equals(filePaths.get(1)));

		List<FileComponent> actionsList = new ArrayList<FileComponent>(actionsToCheck);

		//poll elements from the queue, check state and file path for each of them
		FileComponent head = actionsList.get(0);
		assertTrue(actionsToCheck.size() == 3);
		assertTrue(head.getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(head.getPath().toString().equals(filePaths.get(1)));

		head = actionsList.get(1);
		assertTrue(head.getAction().getCurrentState() instanceof LocalHardDeleteState);
		assertTrue(head.getPath().toString().equals(filePaths.get(0)));

		head = actionsList.get(2);
		assertTrue(head.getAction().getCurrentState() instanceof LocalMoveState);
		System.out.println("head.getAction().getFilePath().toString(): " + head.getPath().toString());
		System.out.println("filePaths.get(3): " + filePaths.get(3));
		assertTrue(head.getPath().toString().equals(filePaths.get(3)));

		long end = System.currentTimeMillis();

		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);

		//cleanup:

//		deleteFile(Paths.get(filePaths.get(0)));
		deleteFile(Paths.get(filePaths.get(1)));
//		deleteFile(Paths.get(filePaths.get(2)));
		deleteFile(Paths.get(filePaths.get(3)));
		assertTrue(manager.getFileTree().getFile(files.get(0).toPath()) == null);
		assertTrue(manager.getFileTree().getFile(files.get(1).toPath()) == null);
		assertTrue(manager.getFileTree().getFile(files.get(2).toPath()) == null);
		assertTrue(manager.getFileTree().getFile(files.get(3).toPath()) == null);

		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 5);
	}

	private void printQueue(BlockingQueue<FileComponent> queue) {
		Vector<FileComponent> files = new Vector<FileComponent>(queue);
		for(int i = 0; i < files.size(); i++){
			System.out.println(i + ". File :" + files.get(i).getPath() + " - " + files.get(i).getAction().getCurrentState());
		}
	}

	private void deleteFile(Path filePath){
		System.out.println("Hard delete file " + filePath);
		manager.onLocalFileHardDelete(filePath);
		sleepMillis(200);
		manager.onLocalFileDeleted(filePath);
		sleepMillis(10);
	}

	/**
	 * This test simulates the the process of creating AND moving/renaming a file
	 * before the upload to the network was triggered. Therefore, the old file should
	 * be ignored (initial state, where execute does nothing) and the new file should
	 * be pushed as a create.
	 */

	@Test
	public void createOnLocalMove(){

		//sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS*3);
		long start = System.currentTimeMillis();

		BlockingQueue<FileComponent> actionsToCheck = manager.getFileComponentQueue().getQueue();;
		assertTrue(actionsToCheck.size() == 0);

		manager.onLocalFileCreated(Paths.get(filePaths.get(4)));
		sleepMillis(10);

		//move the file LOCALLY
		manager.onLocalFileDeleted(Paths.get(filePaths.get(4)));
		sleepMillis(10);

		manager.onLocalFileCreated(Paths.get(filePaths.get(5)));
		//sleepMillis(10);

		FileComponent head = actionsToCheck.peek();
		System.out.println("actionsToCheck.size(): " + actionsToCheck.size());
		ArrayList<FileComponent> array = new ArrayList<FileComponent>(actionsToCheck);
		for(FileComponent comp : array){
			System.out.println(comp.getPath() + ": " + comp.getAction().getCurrentState().getClass().toString());
		}
		assertTrue(actionsToCheck.size() == 2);
		assertTrue(array.get(0).getAction().getCurrentState() instanceof InitialState);
		assertTrue(array.get(0).getPath().toString().equals(filePaths.get(4)));
		assertTrue(array.get(1).getAction().getCurrentState() instanceof LocalCreateState);
		assertTrue(array.get(1).getPath().toString().equals(filePaths.get(5)));

		long end = System.currentTimeMillis();
		assertTrue(end - start <= ActionExecutor.ACTION_WAIT_TIME_MS);
		sleepMillis(ActionExecutor.ACTION_WAIT_TIME_MS * 5);
	}

	/**
	 * Wait the defined time interval. Useful to guarantee different timestamps in
	 * milliseconds if events are programatically created. Furthermore allows to wait
	 * for a cleaned action queue if ActionExecutor.ACTION_TIME_TO_WAIT * 2 is passed
	 * as millisToSleep
	 */
	public static void sleepMillis(long millisToSleep){
		try {
			Thread.sleep(millisToSleep);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
