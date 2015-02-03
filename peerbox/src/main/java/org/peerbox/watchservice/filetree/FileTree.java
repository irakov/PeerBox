package org.peerbox.watchservice.filetree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.hive2hive.core.events.framework.interfaces.file.IFileEvent;
import org.peerbox.watchservice.FileEventManager;
import org.peerbox.watchservice.FileWalker;
import org.peerbox.watchservice.IFileEventManager;
import org.peerbox.watchservice.filetree.composite.FileComponent;
import org.peerbox.watchservice.filetree.composite.FileLeaf;
import org.peerbox.watchservice.filetree.composite.FolderComposite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;

public class FileTree implements IFileTree{

	private static final Logger logger = LoggerFactory.getLogger(FileTree.class);

	private FolderComposite rootOfFileTree;
	private Map<String, FolderComposite> deletedByContentNamesHash = new ConcurrentHashMap<String, FolderComposite>();
	private SetMultimap<String, FileComponent> deletedByContentHash = HashMultimap.create();
    private boolean maintainContentHashes;

    @Inject
    public FileTree(Path rootPath){
    	maintainContentHashes = true;
		rootOfFileTree = new FolderComposite(rootPath, true, true);
	}

    /**
     * @param rootPath is the root folder of the tree
     * @param maintainContentHashes set to true if content hashes have to be maintained. Content hash changes are
     * then propagated upwards to the parent directory.
     */
    public FileTree(Path rootPath, boolean maintainContentHashes){
    	this.maintainContentHashes = maintainContentHashes;
		rootOfFileTree = new FolderComposite(rootPath, maintainContentHashes, true);
	}

    public boolean getMaintainContentHashes(){
    	return maintainContentHashes;
    }

	@Override
	public void putFile(Path dstPath, FileComponent fileToPut) {
		rootOfFileTree.putComponent(dstPath, fileToPut);
	}

	@Override
	public FileComponent getFile(Path fileToGet) {
		return rootOfFileTree.getComponent(fileToGet);
	}

	public FileComponent getOrCreateFileComponent(Path path, Boolean isFile, IFileEventManager eventManager) {
//		FileComponent file = fileTree.getComponent(path.toString());
		FileComponent file = getFile(path);
		if(file == null){
			logger.trace("FileComponent {} is new and now created.", path);
			if(isFile == null){
				logger.trace("FileComponent {} has no fileevent.", path);
				//TODO check for directory wrong if it does not exist yet!
				file = createFileComponent(path, Files.isRegularFile(path));
			} else {
				logger.trace("FileComponent {} has a fileevent isfile= {}", path, isFile);
				file = createFileComponent(path, isFile);
			}

			file.getAction().setFile(file);
			file.getAction().setFileEventManager(eventManager);
		}
		logger.debug("File {} has state {}", file.getPath(), file.getAction().getCurrentStateName());
		return file;
	}

	public FileComponent getOrCreateFileComponent(Path path, IFileEventManager eventManager){
		return getOrCreateFileComponent(path, null, eventManager);
	}

	private FileComponent createFileComponent(Path path, boolean isFile) {
		FileComponent component = null;
		if (isFile) {
			logger.trace("FileComponent {} created.", path);
			component = new FileLeaf(path, getMaintainContentHashes());
		} else {
			logger.trace("FolderComponent {} created.", path);
			component = new FolderComposite(path, getMaintainContentHashes());
		}

//		getSynchronizedFiles().add(path);
		return component;
	}

	@Override
	public FileComponent deleteFile(Path fileToDelete) {
		return rootOfFileTree.deleteComponent(fileToDelete);
	}

	@Override
	public FileComponent updateFile(Path fileToUpdate) {
		return null;
	}


	public Map<String, FolderComposite> getDeletedByContentNamesHash() {
		return deletedByContentNamesHash;
	}

	public SetMultimap<String, FileComponent> getDeletedByContentHash(){
		return deletedByContentHash;
	}

//    public Set<Path> getSynchronizedFiles(){
////    	return synchronizedFiles;
//    	return null;
//    }

	/**
	 * This function runs the FileWalker to discover the structure of the subtree
	 * at the given location. This means, content hashes are neither computed nor
	 * propagated upwards. The structure is represented using a hash on the names
	 * of the contained objects of each folder
	 * @param filePath represents the root of the subtree
	 * @return the hash representing the folder's structure
	 */
	public String discoverSubtreeStructure(Path filePath, FileEventManager manager) {
		FileWalker walker = new FileWalker(filePath, manager);
		logger.debug("start discovery of subtree structure at : {}", filePath);
		walker.indexNamesRecursively();
		return walker.getContentNamesHashOfWalkedFolder();
	}

	/**
	 * This function runs the FileWalker to discover the complete content of a subtree
	 * at the given location. The content hash of each file is computed, the content hash
	 * of a folder consists of a hash over contained files' content hashes. If these hashes
	 * change, the change is propagated to the parent folder
	 * @param filePath represents the root of the subtree
	 * @return the complete subtree as a FolderComposite
	 */
	public FolderComposite discoverSubtreeCompletely(Path filePath, FileEventManager manager) {
		FileWalker walker = new FileWalker(filePath, manager);
		logger.debug("start complete subtree discovery at : {}", filePath);
		return walker.indexContentRecursively();
	}

	/**
	 * Searches the SetMultiMap<String, FileComponent> deletedByContentHash for
	 * a deleted FileComponent with the same content hash. If several exist, the temporally
	 * closest is returned.
	 * @param createdComponent The previously deleted component
	 * @return
	 */
	public FileComponent findDeletedByContent(FileComponent createdComponent){
		FileComponent deletedComponent = null;
		String contentHash = createdComponent.getContentHash();
		logger.trace("Contenthash to search for: {}", contentHash);
		Set<FileComponent> deletedComponents = getDeletedByContentHash().get(contentHash);


		for(FileComponent comp : deletedComponents){
			logger.trace("Compoonent {} with hash {}", comp.getPath(), comp.getContentHash());
		}
		long minTimeDiff = Long.MAX_VALUE;
		for(FileComponent candidate : deletedComponents) {
			long timeDiff = createdComponent.getAction().getTimestamp() - candidate.getAction().getTimestamp();
			if(timeDiff < minTimeDiff) {
				minTimeDiff = timeDiff;
				deletedComponent = candidate;
			}
		}
		deletedComponents.remove(deletedComponent);
		return deletedComponent;
	}

	public Path getRootPath(){
		return rootOfFileTree.getPath();
	}

	@Override
	public Set<Path> getSynchronizedPathsAsSet(){
		Set<Path> synchronizedFiles = new ConcurrentHashSet<Path>();
		rootOfFileTree.getSynchronizedChildrenPaths(synchronizedFiles);
		return synchronizedFiles;
	}
}
