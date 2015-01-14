package org.peerbox.watchservice.filetree.composite;

import java.nio.file.Path;
import java.util.Set;

import org.peerbox.exceptions.NotImplException;
import org.peerbox.watchservice.Action;
import org.peerbox.watchservice.IAction;

abstract class AbstractFileComponent implements FileComponent {

	private final IAction action;
	private Path path;
	private String contentHash;
	private boolean isSynchronized;
	private boolean isUploaded;
	protected final boolean updateContentHash;

	private FolderComposite parent;

	protected AbstractFileComponent(final Path path, final boolean updateContentHash) {
		this.action = new Action();
		this.path = path;
		this.contentHash = "";
		this.updateContentHash = updateContentHash;
		this.isUploaded = false;
		this.isSynchronized = false;
	}

	@Override
	public final IAction getAction() {
		return action;
	}

	@Override
	public final boolean isUploaded() {
		return isUploaded;
	}

	@Override
	public final void setIsUploaded(boolean isUploaded) {
		this.isUploaded = isUploaded;
	}

	@Override
	public final Path getPath() {
		return this.path;
	}

	@Override
	public final void setPath(Path path) {
		this.path = path;
	}

	@Override
	public final FolderComposite getParent() {
		return parent;
	}

	@Override
	public final void setParent(final FolderComposite parent) {
		this.parent = parent;
	}

	@Override
	public final String getContentHash() {
		return contentHash;
	}

	protected final void setContentHash(String contentHash) {
		this.contentHash = contentHash;
	}

	@Override
	public final boolean bubbleContentHashUpdate() {
		boolean hasChanged = updateContentHash();
		if (hasChanged && getParent() != null) {
			getParent().bubbleContentHashUpdate();
		}
		return hasChanged;
	}

	protected abstract boolean updateContentHash();

	@Override
	public final boolean isSynchronized() {
		return isSynchronized;
	}

	@Override
	public void setIsSynchronized(boolean isSynchronized) {
		this.isSynchronized = isSynchronized;
	}

	@Override
	public final boolean isFolder() {
		return !isFile();
	}

	@Override
	public void putComponent(Path path, FileComponent component) {
		String msg = String.format("putComponent not implemented. "
				+ "This is probably a file. "
				+ "(this=%s, parameter=%s)", getPath(), path);

		throw new NotImplException(msg);
	}

	@Override
	public FileComponent deleteComponent(Path path) {
		String msg = String.format("deleteComponent not implemented. "
				+ "This is probably a file. "
				+ "(this=%s, parameter=%s)", getPath(), path);

		throw new NotImplException(msg);
	}

	@Override
	public FileComponent getComponent(Path path) {
		String msg = String.format("getComponent not implemented. "
				+ "This is probably a file. "
				+ "(this=%s, parameter=%s)", getPath(), path);

		throw new NotImplException(msg);
	}

	@Override
	public String getStructureHash() {
		String msg = String.format("getStructureHash not implemented. "
				+ "This is probably a file. "
				+ "(this=%s)", getPath());

		throw new NotImplException(msg);
	}

	@Override
	public void setStructureHash(String hash) {
		String msg = String.format("setStructureHash not implemented. "
				+ "This is probably a file. "
				+ "(this=%s, hash=%s)", getPath(), hash);

		throw new NotImplException(msg);
	}

	@Override
	public void getSynchronizedChildrenPaths(Set<Path> synchronizedPaths) {
		String msg = String.format("getSynchronizedChildrenPaths not implemented. "
				+ "This is probably a file. "
						+ "(this=%s)", getPath());

//		throw new NotImplException(msg);
	}

}
