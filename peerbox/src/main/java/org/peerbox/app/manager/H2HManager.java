package org.peerbox.app.manager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.hive2hive.core.api.H2HNode;
import org.hive2hive.core.api.configs.FileConfiguration;
import org.hive2hive.core.api.configs.NetworkConfiguration;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.api.interfaces.IH2HNode;
import org.hive2hive.core.api.interfaces.INetworkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

@Singleton
public final class H2HManager implements IH2HManager {

	private static final Logger logger = LoggerFactory.getLogger(H2HManager.class);

	private IH2HNode node;
	private INetworkConfiguration networkConfiguration;

	@Override
	public IH2HNode getNode() {
		return node;
	}

	private String generateNodeID() {
		return UUID.randomUUID().toString();
	}

	private void createNode() {
		IFileConfiguration fileConfig = FileConfiguration.createDefault();
		node = H2HNode.createNode(fileConfig);
	}
	
	@Override
	public boolean joinNetwork(List<String> bootstrappingNodes) {
		boolean connected = false;
		Iterator<String> nodeIt = bootstrappingNodes.iterator();
		while (nodeIt.hasNext() && !connected) {
			String node = nodeIt.next();
			boolean res = false;
			try {
				res = joinNetwork(node);
				connected = isConnected();
				if (res && connected) {
					logger.debug("Successfully connected to node {}", node);
				} else {
					logger.debug("Could not connect to node {}", node);
				}
			} catch(UnknownHostException e) {
				logger.warn("Address of host could not be determined: {}", node);
				res = false;
				connected = false;
			}
		}
		return connected;
	}
	
	@Override
	public boolean joinNetwork(String address) throws UnknownHostException {
		if (address.isEmpty()) {
			throw new IllegalArgumentException("Bootstrap address is empty.");
		}
		
		createNode();
		String nodeID = generateNodeID();
		InetAddress bootstrapInetAddress = InetAddress.getByName(address);
		networkConfiguration = NetworkConfiguration.create(nodeID, bootstrapInetAddress);
		return node.connect(networkConfiguration);
	}
	
	@Override
	public boolean createNetwork() {
		createNode();
		String nodeID = generateNodeID();
		networkConfiguration = NetworkConfiguration.createInitial(nodeID);
		return node.connect(networkConfiguration);
	}
	
	@Override
	public boolean leaveNetwork() {
		if (node != null) {
			boolean res = node.disconnect();
			node = null;
			return res;
		}
		
		return true;
	}
	
	@Override
	public boolean isConnected() {
		return node != null && node.isConnected();
	}
	
	@Override
	public INetworkConfiguration getNetworkConfiguration() {
		return networkConfiguration;
	}
}