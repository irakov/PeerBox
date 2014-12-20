package org.peerbox.watchservice;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLeaf extends AbstractFileComponent {
	
	private static final Logger logger = LoggerFactory.getLogger(FileLeaf.class);
	
	public FileLeaf(Path path, boolean updateContentHashes) {
		super(path, updateContentHashes);
		
		if (updateContentHashes) {
			updateContentHash();
		}
	}
	
	/**
	 * Computes and updates this FileLeafs contentHash property.
	 * @return true if the contentHash hash changed, false otherwise.
	 * @param newHash provided content hash. If this is null, the content hash is
	 * calculated on the fly. If this is not null, it is assumed to be the correct
	 * hash of the file's content at the time of the call.
	 */
	@Override
	public boolean updateContentHash() {
		String newHash = PathUtils.computeFileContentHash(path);
		boolean hashUpdated = !contentHash.equals(newHash);
		if (hashUpdated) {
			contentHash = newHash;
			bubbleContentHashUpdate();
		}
		// logger.debug("Content hash update: {}, {}", hashUpdated, contentHash);
		return hashUpdated;
	}

	@Override
	public boolean isFile() {
		return true;
	}

	@Override
	public boolean isReady() {
		return parent.getActionIsUploaded();
	}
}
