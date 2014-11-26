package org.peerbox.model;

import org.peerbox.FileManager;
import org.peerbox.UserConfig;
import org.peerbox.utils.OsUtils;
import org.peerbox.utils.WinRegistry;
import org.peerbox.watchservice.FileEventManager;
import org.peerbox.watchservice.FolderWatchService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PeerBoxClient {
	private UserConfig userConfig;
	
	private FileEventManager fileEventManager;
	private FileManager fileManager;
	private FolderWatchService watchService;
	
	@Inject
	public PeerBoxClient(FileManager fileManager, UserConfig userConfig) {
		this.fileManager = fileManager;
		this.userConfig = userConfig;
	}
	
	public void start() throws Exception {
		// create managers and initialization
//		fileManager = new FileManager(node.getFileManager()); --> provided by guice
		fileEventManager = new FileEventManager(userConfig.getRootPath(), true);
		watchService = new FolderWatchService(userConfig.getRootPath());
		watchService.addFileEventListener(fileEventManager);
		
		fileEventManager.setFileManager(fileManager);
		fileManager.setFileEventManager(fileEventManager);
		
		fileManager.getH2HFileManager().subscribeFileEvents(fileEventManager);
		
		watchService.start();
		
		if(OsUtils.isWindows()) {
			WinRegistry.setApiServerPort(30000);
			WinRegistry.setRootPath(userConfig.getRootPath());
		}
	}
	
}