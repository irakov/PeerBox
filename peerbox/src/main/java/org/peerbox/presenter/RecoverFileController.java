package org.peerbox.presenter;

import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.IFileVersion;
import org.hive2hive.core.processes.files.recover.IVersionSelector;
import org.hive2hive.processframework.exceptions.InvalidProcessStateException;
import org.peerbox.FileManager;
import org.peerbox.interfaces.IFileVersionSelectorEventListener;

import com.google.inject.Inject;

public class RecoverFileController  implements Initializable, IFileVersionSelectorEventListener {
	
	private final StringProperty fileToRecoverProperty;
	private Path fileToRecover;
	
	@FXML
	private TableView<IFileVersion> tblFileVersions;
	@FXML
	private TableColumn<IFileVersion, Integer> tblColIndex;
	@FXML
	private TableColumn<IFileVersion, String> tblColDate;
	@FXML
	private TableColumn<IFileVersion, String> tblColSize;
	@FXML
	private Label lblNumberOfVersions;
	@FXML
	private Button btnRecover;
	
	private final ObservableList<IFileVersion> fileVersions;
	
	private FileVersionSelector versionSelector;

	private FileManager fileManager;
	
	public RecoverFileController() {
		this.fileToRecoverProperty = new SimpleStringProperty();
		fileVersions = FXCollections.observableArrayList();
		versionSelector = new FileVersionSelector(this);
	}
	
	@Inject
	public void setFileManager(FileManager fileManager) {
		this.fileManager = fileManager;
	}
	
	public void setFileToRecover(Path fileToRecover) {
		this.fileToRecover = fileToRecover;
		this.fileToRecoverProperty.setValue(fileToRecover.toString());
		loadVersions();
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		
		initializeTable();
		
		lblNumberOfVersions.textProperty().bind(Bindings.size(fileVersions).asString());
		btnRecover.disableProperty().bind(tblFileVersions.getSelectionModel().selectedItemProperty().isNull());
	}
	
	private void loadVersions() {
		
		try {
			fileManager.recover(fileToRecover.toFile(), versionSelector);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSessionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoPeerConnectionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidProcessStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		// DUMMY DATA!
		
//		fileVersions.add(new IFileVersion() {
//	
//			@Override
//			public int getIndex() {
//				return 1;
//			}
//	
//			@Override
//			public BigInteger getSize() {
//				return BigInteger.valueOf(1234567);
//			}
//	
//			@Override
//			public long getDate() {
//				return System.currentTimeMillis();
//			}
//			
//		});
//		
//		fileVersions.add(new IFileVersion() {
//	
//			@Override
//			public int getIndex() {
//				return 2;
//			}
//	
//			@Override
//			public BigInteger getSize() {
//				return BigInteger.valueOf(12345670);
//			}
//	
//			@Override
//			public long getDate() {
//				return 11113112;
//			}
//			
//		});
	}

	private void sortTable() {
		// sorting by index DESC
		tblColIndex.setSortType(TableColumn.SortType.DESCENDING);
		tblFileVersions.getSortOrder().add(tblColIndex);
		tblFileVersions.sort();
	}

	private void initializeTable() {
		tblFileVersions.setItems(fileVersions);
		initializeColumns();
		sortTable();
	}

	private void initializeColumns() {
		tblColIndex.setCellValueFactory(
				new Callback<CellDataFeatures<IFileVersion, Integer>, ObservableValue<Integer>>() {
					public ObservableValue<Integer> call(CellDataFeatures<IFileVersion, Integer> p) {
						// p.getValue() returns the IFileVersion instance for a particular TableView row
						return new SimpleIntegerProperty(p.getValue().getIndex()).asObject();
					}
				}
		);
		
		tblColDate.setCellValueFactory(
				new Callback<CellDataFeatures<IFileVersion, String>, ObservableValue<String>>() {
					public ObservableValue<String> call(CellDataFeatures<IFileVersion, String> p) {
						
						Date date = new Date(p.getValue().getDate());
						DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						String dateFormatted = formatter.format(date);
						return new SimpleStringProperty(dateFormatted);
					}
				}
		);
		
		tblColSize.setCellValueFactory(
				new Callback<CellDataFeatures<IFileVersion, String>, ObservableValue<String>>() {
					public ObservableValue<String> call(CellDataFeatures<IFileVersion, String> p) {
						String humanReadableSize = FileUtils.byteCountToDisplaySize(p.getValue().getSize());
						return new SimpleStringProperty(humanReadableSize);
					}
				}
		);
	}

	public void cancelAction(ActionEvent event) {
		versionSelector.selectVersion((IFileVersion)null);
	}
	
	public void recoverAction(ActionEvent event) {
		IFileVersion selectedVersion = tblFileVersions.getSelectionModel().getSelectedItem();
		versionSelector.selectVersion(selectedVersion);
	}
	
	
	public String getFileToRecover() {
		return fileToRecoverProperty.get();
	}

	public void setFileToRecover(String fileName) {
		this.fileToRecoverProperty.set(fileName);
	}

	public StringProperty fileToRecoverProperty() {
		return fileToRecoverProperty;
	}
	
	@Override
	public void onAvailableVersionsReceived(List<IFileVersion> availableVersions) {
		Platform.runLater(() -> {
			fileVersions.addAll(availableVersions);
			sortTable();
		});
	}
	
	private class FileVersionSelector implements IVersionSelector {
		
		private final Lock selectionLock = new ReentrantLock();
		private final Condition versionSelectedCondition  = selectionLock.newCondition();
		private IFileVersionSelectorEventListener listener; 
		private IFileVersion selectedVersion;
		
		private volatile boolean gotAvailableVersions = false;
		private volatile boolean isCancelled = false;
		
		// TODO: implement cancelling
		
		public FileVersionSelector(IFileVersionSelectorEventListener listener) {
			if(listener == null) {
				throw new IllegalArgumentException("Argument listener must not be null.");
			}
			
			this.listener = listener;
		}
		
		public void selectVersion(IFileVersion selectedVersion) {
			if(selectedVersion == null) {
				isCancelled = true;
			}
			
			if(gotAvailableVersions) {
				try {
					selectionLock.lock();
					this.selectedVersion = selectedVersion;
					versionSelectedCondition.signal();
				} finally {
					selectionLock.unlock();
				}
			}
		}

		@Override
		public IFileVersion selectVersion(List<IFileVersion> availableVersions) {
			
			if(isCancelled) {
				return null;
			}
			
			selectionLock.lock();
			try {
				if(availableVersions != null) {
					gotAvailableVersions = true;
					listener.onAvailableVersionsReceived(availableVersions);
					versionSelectedCondition.awaitUninterruptibly();
				}
			} finally {
				selectionLock.unlock();
			}
			
			return selectedVersion;
		}

		@Override
		public String getRecoveredFileName(String fullName, String name, String extension) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

	
}
