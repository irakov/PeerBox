package org.peerbox.watchservice;

import java.nio.file.Path;

public abstract class AbstractFileComponent implements FileComponent {
	
	protected IAction action;
	protected Path path;
	protected FolderComposite parent;
	protected String contentHash;
	protected String contentNamesHash;
	protected boolean updateContentHashes;
	
	public AbstractFileComponent(Path path, boolean updateContentHashes) {
		this.path = path;
		this.action = new Action();
		this.contentHash = "";
		this.contentNamesHash = "";
		this.updateContentHashes = updateContentHashes;
		
		if (updateContentHashes) {
			updateContentHash();
		}
	}

	@Override
	public IAction getAction() {
		return this.action;
	}
	
	@Override
	public boolean getActionIsUploaded() {
		return this.getAction().getIsUploaded();
	}
	
	@Override
	public void setActionIsUploaded(boolean isUploaded) {
		this.getAction().setIsUploaded(isUploaded);
	}
	
	@Override
	public Path getPath() {
		return this.path;
	}
	
	@Override
	public void setPath(Path path) {
		this.path = path;
	}
	
	protected Path getName() {
		return path.getFileName();
	}
	
	@Override
	public void setParentPath(Path parentPath){	
		if(parentPath != null){
			this.path = parentPath.resolve(getName());
		}
	}
	
	@Override
	public FolderComposite getParent() {
		return this.parent;
	}
	
	@Override
	public void setParent(FolderComposite parent) {
		this.parent = parent;
	}
	
	@Override
	public abstract boolean isFile();
	
	@Override
	public boolean isFolder() {
		return !isFile();
	}
	
	@Override
	public String getContentHash() {
		return contentHash;
	}
	
	@Override
	public abstract boolean updateContentHash();
	
	protected void bubbleContentHashUpdate() {
		if (parent != null) {
			parent.updateContentHash();
		}
	}
	
	@Override
	public String getStructureHash() {
		return contentNamesHash;
	}

	@Override
	public void setStructureHash(String hash) {
		contentNamesHash = hash;
	}
	
	@Override
	public FileComponent getComponent(String path) {
		// empty default implementation
		return null;
	}
	
	@Override
	public void putComponent(String path, FileComponent component) {
		// empty default implementation
	}

	@Override
	public FileComponent deleteComponent(String path) {
		// empty default implementation
		return null;
	}
}
