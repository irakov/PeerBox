package org.peerbox.watchservice;

import java.nio.file.Path;
import java.util.Base64;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.hive2hive.core.security.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * @author Claudio
 * Folder composite represents a file system directory.
 */
public class FolderComposite extends AbstractFileComponent {

	private static final Logger logger = LoggerFactory.getLogger(FolderComposite.class);
	
	private final SortedMap<String, FileComponent> children;
	private boolean isRoot = false;
	
	public FolderComposite(Path path, boolean updateContentHashes) {
		this(path, updateContentHashes, false);
	}
	
	public FolderComposite(Path path, boolean updateContentHashes, boolean isRoot) {
		super(path, updateContentHashes);
		
		children = new ConcurrentSkipListMap<String, FileComponent>();
		
		this.isRoot = isRoot;
		if (isRoot) {
			action.setIsUploaded(true);
		}
		
		if (updateContentHashes) {
			updateContentHash();
		}
	}
	
	/**
	 * Get the FileComponent at the specified location. Triggers updates of content and name hashes.
	 * @return If it does exist, the requested FileComponent is returned, null otherwise.
	 */
	@Override
	public synchronized FileComponent getComponent(String remainingPath){
		//if the path it absolute, cut off the absolute path to the root directory!
		logger.debug("Root: {} FilePath: {}", path, remainingPath);
		if(remainingPath.startsWith(path.toString())){
			remainingPath = remainingPath.substring(path.toString().length() + 1);
		}
		
		String nextLevelPath = PathUtils.getNextPathFragment(remainingPath);
		String newRemainingPath = PathUtils.getRemainingPathFragment(remainingPath);
		
		FileComponent nextLevelComponent = children.get(nextLevelPath);
//		for(Map.Entry<String, FileComponent> child : children.entrySet()){
//			logger.debug("{} has child {}", getPath(), child.getKey());
//		}
		
		if(newRemainingPath.equals("")){
//			if(nextLevelComponent != null && updateContentHashes){
//				nextLevelComponent.bubbleContentHashUpdate();
//			}
//			bubbleContentNamesHashUpdate();
			return children.get(nextLevelPath);
		} else {
			if(nextLevelComponent == null){
				return null;
			} else {
				return nextLevelComponent.getComponent(newRemainingPath);
			}
		}
	}

	/**
	 * Appends a new component to the FolderComposite. Inexistent folders are added on the
	 * fly. Existing items are replaced. Triggers updates of content and name hashes.
	 */
	@Override
	public synchronized void putComponent(String remainingPath, FileComponent component) {
//		component.getAction().setPath(Paths.get(remainingPath));
		//if the path it absolute, cut off the absolute path to the root directory!
		if(remainingPath.startsWith(path.toString())){
			remainingPath = remainingPath.substring(path.toString().length() + 1);
		}
		//logger.trace("after remainingpath calculation {}", remainingPath);
		String nextLevelPath = PathUtils.getNextPathFragment(remainingPath);
		String newRemainingPath = PathUtils.getRemainingPathFragment(remainingPath);
		
		FileComponent nextLevelComponent;
		if(newRemainingPath == null){
			//logger.debug("newRemainingPath == null");
		}
		//if we are at the last recursion, perform the add, else recursively continue
		if(newRemainingPath.equals("")){
			//logger.trace("newRemainingPath is empty {}", remainingPath);
			if(nextLevelPath == null){
				//logger.trace("nextLevelPath is NULL {}", remainingPath);
			}
			//logger.trace("nextLevelPath is {}", nextLevelPath);
			addComponentToChildren(nextLevelPath, component);
			//logger.trace("after addComponentToChildren {}", nextLevelPath);
		} else {
			//logger.trace("newRemainingPath is not empty {}", remainingPath);
			nextLevelComponent = children.get(nextLevelPath);
			//logger.trace("after children.get {}", remainingPath);
			if(nextLevelComponent == null){
				//logger.trace("newLevelComponent is null {}", remainingPath);
				Path completePath = constructFullPath(nextLevelPath);
				//logger.trace("after completePathConstrution{} {}", remainingPath, completePath);
				nextLevelComponent = new FolderComposite(completePath, updateContentHashes);
				//logger.trace("after new FolderComposite( {} {}", remainingPath, completePath);
				addComponentToChildren(nextLevelPath, nextLevelComponent);
				//logger.trace("after addComponentToChildren2 {} {}", nextLevelPath, nextLevelComponent);
			}

			nextLevelComponent.putComponent(newRemainingPath, component);
		//	logger.trace("success nextLevelComponent.putComponen", remainingPath);
		}
		//logger.trace("success putComponent", nextLevelPath);
	} 

	/**
	 * Deletes the FileComponent at location remainingPath. Triggers updates of 
	 * content and name hashes. 
	 * @return The deleted component. If it does not exist, null is returned
	 */
	@Override
	public FileComponent deleteComponent(String remainingPath) {
		if(remainingPath.startsWith(path.toString())){
			remainingPath = remainingPath.substring(path.toString().length() + 1);
		}
		
		String nextLevelPath = PathUtils.getNextPathFragment(remainingPath);
		String newRemainingPath = PathUtils.getRemainingPathFragment(remainingPath);
		
		FileComponent nextLevelComponent = children.get(nextLevelPath);
		
		if(newRemainingPath.equals("")){
			FileComponent removed = children.remove(nextLevelPath);
			logger.debug("Removed {}", removed.getPath());
			if(updateContentHashes){
				bubbleContentHashUpdate();
			}
			bubbleContentNamesHashUpdate();
			return removed;
		} else {
			if(nextLevelComponent == null){
				return null;
			} else {
				FileComponent deletedComponent = nextLevelComponent.deleteComponent(newRemainingPath);
				return deletedComponent;	
			}
		}
	}

	/**
	 * Computes the content hash for this object by appending the content hashes of contained
	 * components and hashing over it again. 
	 * @return
	 */
	private boolean computeContentNamesHash() {
		StringBuilder nameHashInput = new StringBuilder();
		String oldNamesHash = contentNamesHash;
		for (String childName : children.keySet()) {
			nameHashInput.append(childName);
		}
		contentNamesHash = PathUtils.createStringFromByteArray(HashUtil.hash(nameHashInput.toString().getBytes()));
		if (!contentNamesHash.equals(oldNamesHash)) {
			return true;
		} else {
			return false;
		}
	}
	
	private void bubbleContentNamesHashUpdate() {
		// logger.debug("Structure hash of {} before: {}", path, contentNamesHash);
		boolean hasChanged = computeContentNamesHash();
		// logger.debug("Structure hash of {} after: {}", path, contentNamesHash);
		// logger.debug("successful computeContentNamesHash hasChanged {}", hasChanged);
		if (hasChanged && parent != null) {
			// logger.debug("start partent.bubbleContentNamesHashUpdate");
			parent.bubbleContentNamesHashUpdate();
			// logger.debug("finish partent.bubbleContentNamesHashUpdate");
		}
	}

	/*
	 * Because of the new children, the content hash of the directory may change and is propagated
	 */
	private void addComponentToChildren(String nextLevelPath, FileComponent component) {
		children.remove(nextLevelPath);
		children.put(nextLevelPath, component);
//		logger.trace("after remove/put {}", nextLevelPath);
//		if(component == null){
//			logger.trace("COMPONENT IS NULL");
//		}
		component.setParent(this);
		logger.trace("SET Parent for {} is {}", component.getPath(), getPath());
//		logger.trace("after setParent {}", nextLevelPath);
		if(updateContentHashes){
//			logger.trace("START bubbleContentHashUpdate {}", nextLevelPath);
			bubbleContentHashUpdate();		
//			logger.trace("END bubbleContentHashUpdate {}", nextLevelPath);
		}
//		logger.trace("after bubbleContentHashUpdate {}", nextLevelPath);
		component.setParentPath(path);
//		logger.trace("after setPath {}", nextLevelPath);
		if(component instanceof FolderComposite){
			FolderComposite componentAsFolder = (FolderComposite)component;
			componentAsFolder.propagatePathChangeToChildren();
		}
//		logger.trace("BEFORE bubbleContentNamesHashUpdate {}", nextLevelPath);
		bubbleContentNamesHashUpdate();
//		logger.trace("successful bubbleContentNamesHashUpdate {}", nextLevelPath);
	}

	/**
	 * If a subtree is appended, the children of the subtree need to update their paths.
	 * This function starts a recursive update. Furthermore, the filePath of the action
	 * related to each FileComponent is updates as well.
	 * @param parentPath
	 */
	private void propagatePathChangeToChildren() {
		// System.out.println("getPath(): " + getPath());
		for (FileComponent child : children.values()) {
			child.setParentPath(getPath());
			if (child instanceof FolderComposite) {
				FolderComposite childAsFolder = (FolderComposite) child;
				childAsFolder.propagatePathChangeToChildren();
			}
		}
	}
	
	private Path constructFullPath(String lastPathFragment) {
		Path completePath = path.resolve(lastPathFragment);
		logger.debug("CompletePath: {}", completePath);
		return completePath;
	}

	@Override
		public boolean updateContentHash() {
	//		logger.debug("enter updateContentHash(String) in FolderComposite()");
			StringBuilder sb = new StringBuilder();
			for(FileComponent value : children.values()){
			//	logger.debug("value: {}", value.getPath());
				sb.append(value.getContentHash());
			}
	//		logger.trace("read children");
			byte[] rawHash = HashUtil.hash(sb.toString().getBytes());
			String newHash = Base64.getEncoder().encodeToString(rawHash);
	//		logger.trace("got newHash");
			if(!contentHash.equals(newHash)){
				contentHash = newHash;
				bubbleContentHashUpdate();
				return true;
			}
	//		logger.trace("successful no new hash");
			return false;
		}

	@Override
	public void setActionIsUploaded(boolean isUploaded) {
		this.getAction().setIsUploaded(isUploaded);
		for (FileComponent child : children.values()) {
			child.getAction().setIsUploaded(isUploaded);
		}
	}

	public SortedMap<String, FileComponent> getChildren() {
		return children;
	}

	@Override
	public boolean isFile() {
		return false;
	}
	
	public boolean isRoot() {
		return isRoot;
	}

	@Override
	public boolean isReady() {
		if (isRoot) {
			return true;
		} else {
			return parent.getActionIsUploaded();
		}
	}
}
