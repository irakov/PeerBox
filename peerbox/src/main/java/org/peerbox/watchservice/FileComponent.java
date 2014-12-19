package org.peerbox.watchservice;

import java.nio.file.Path;

public interface FileComponent {

	public Path getPath();

	public void setPath(Path path);

	public void setParentPath(Path parent);

	public boolean isFolder();

	public boolean isFile();

	public String getContentHash();

	public boolean updateContentHash();

//	public boolean updateContentHash(String contentHash);

	public String getStructureHash();

	public void setStructureHash(String hash);

	public IAction getAction();

	public FolderComposite getParent();

	public void setParent(FolderComposite parent);

	/**
	 * This function should propagate a content hash update to the parent FolderComposite
	 * in which this FileComponent is contained.
	 */
//	public void bubbleContentHashUpdate();

	public FileComponent getComponent(String path);

	public void putComponent(String path, FileComponent component);

	public FileComponent deleteComponent(String path);

	public boolean getActionIsUploaded();

	public void setActionIsUploaded(boolean isUploaded);

	public boolean isReady();
}
