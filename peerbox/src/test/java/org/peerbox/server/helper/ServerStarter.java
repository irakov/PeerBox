package org.peerbox.server.helper;

import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.peerbox.guice.ApiServerModule;
import org.peerbox.server.IServer;
import org.peerbox.utils.OsUtils;
import org.peerbox.utils.WinRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ServerStarter {
	private static final Logger logger = LoggerFactory.getLogger(ServerStarter.class);

	public static void main(String[] args) throws Exception {
		
		if(OsUtils.isWindows()) {
			WinRegistry.setRootPath(Paths.get(FileUtils.getUserDirectoryPath(), "PeerBox"));
		}
		
		Injector injector = Guice.createInjector(new ApiServerModule(), new ApiServerTestModule());
		IServer cmdServer = injector.getInstance(IServer.class);
		cmdServer.start();

		// do not exit and wait
		Thread.currentThread().join();
	}
}
