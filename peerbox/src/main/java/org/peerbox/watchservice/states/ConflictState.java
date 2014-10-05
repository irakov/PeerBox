package org.peerbox.watchservice.states;

import java.nio.file.Path;

import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.peerbox.FileManager;
import org.peerbox.watchservice.Action;
import org.peerbox.watchservice.ConflictHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConflictState extends AbstractActionState {

	private final static Logger logger = LoggerFactory.getLogger(ConflictState.class);

	public ConflictState(Action action) {
		super(action);
	}

	@Override
	public AbstractActionState handleLocalCreateEvent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleLocalDeleteEvent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleLocalUpdateEvent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleLocalMoveEvent(Path oldFilePath) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleRemoteDeleteEvent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleRemoteUpdateEvent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AbstractActionState handleRemoteMoveEvent(Path oldFilePath) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void execute(FileManager fileManager) throws NoSessionException,
			NoPeerConnectionException, IllegalFileLocation {
		// TODO Auto-generated method stub
		Path fileInConflict = action.getFilePath();
		Path renamedFile = ConflictHandler.rename(fileInConflict);
		fileInConflict = renamedFile;
	}

}
