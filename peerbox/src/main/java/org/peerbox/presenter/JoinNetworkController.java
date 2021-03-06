package org.peerbox.presenter;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.peerbox.ResultStatus;
import org.peerbox.app.config.BootstrappingNodes;
import org.peerbox.app.config.BootstrappingNodesFactory;
import org.peerbox.app.manager.node.INodeManager;
import org.peerbox.presenter.validation.EmptyTextFieldValidator;
import org.peerbox.presenter.validation.ValidationUtils.ValidationResult;
import org.peerbox.view.ViewNames;
import org.peerbox.view.controls.ErrorLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class JoinNetworkController implements Initializable {

	private static final Logger logger = LoggerFactory.getLogger(JoinNetworkController.class);

	private INodeManager nodeManager;
	private NavigationService fNavigationService;

	private BootstrappingNodes bootstrappingNodes;
	private BootstrappingNodesFactory bootstrappingNodesFactory;

	@FXML
	private VBox vboxForm;
	@FXML
	private TextField txtBootstrapAddress;
	@FXML
	private ComboBox<String> cbBootstrapNodes;
	@FXML
	private ErrorLabel lblError;
	@FXML
	private Label lblBootstrapAddressError;
	@FXML
	private ProgressIndicator piProgress;

	private EmptyTextFieldValidator bootstrapValidator;

	@Inject
	public JoinNetworkController(NavigationService navigationService, INodeManager nodeManager) {
		this.fNavigationService = navigationService;
		this.nodeManager = nodeManager;
	}

	public void initialize(URL arg0, ResourceBundle arg1) {
		Platform.runLater(() -> {
			txtBootstrapAddress.requestFocus();
		});
		initializeValidations();
		loadBootstrapNodes();
	}

	private void loadBootstrapNodes() {
		cbBootstrapNodes.getItems().clear();

		try {
			bootstrappingNodes = bootstrappingNodesFactory.create();
			bootstrappingNodesFactory.load();
			cbBootstrapNodes.getItems().addAll(bootstrappingNodes.getBootstrappingNodes());
			txtBootstrapAddress.setText(bootstrappingNodes.getLastNode());
		} catch (IOException ioex) {
			logger.warn("Could not load bootstrapping nodes from configuration.", ioex);
			setError("Could not load settings.");
		}
	}

	private void initializeValidations() {
		bootstrapValidator = new EmptyTextFieldValidator(txtBootstrapAddress, true, ValidationResult.BOOTSTRAPHOST_EMPTY);
		bootstrapValidator.setErrorProperty(lblBootstrapAddressError.textProperty());
	}

	private void saveJoinConfig() {
		try {
			bootstrappingNodes.getBootstrappingNodes().add(getBootstrapNode());
			bootstrappingNodes.setLastNode(getBootstrapNode());
			bootstrappingNodesFactory.save();
		} catch (IOException ioex) {
			logger.warn("Could not save settings: {}", ioex.getMessage());
			setError("Could not save settings.");
		}
	}

	private void resetForm() {
		loadBootstrapNodes();
		txtBootstrapAddress.clear();
		uninstallProgressIndicator();
		uninstallValidationDecorations();
		vboxForm.disableProperty().unbind();
		vboxForm.setDisable(false);
	}

	private void uninstallValidationDecorations() {
		bootstrapValidator.reset();
	}

	private void installProgressIndicator() {
		Platform.runLater(() -> {
			// center indicator with respect to the grid
			double xOffset = piProgress.getWidth() / 2.0;
			double yOffset = piProgress.getHeight() / 2.0;
			double x = vboxForm.getWidth() / 2.0 - xOffset;
			double y = vboxForm.getHeight() / 2.0 - yOffset;
			piProgress.relocate(x, y);
			// show
			piProgress.setVisible(true);
		});
	}

	private void uninstallProgressIndicator() {
		Platform.runLater(() -> {
			piProgress.setVisible(false);
		});
	}

	public void navigateBackAction(ActionEvent event) {
		if (nodeManager.isConnected()) {
			nodeManager.leaveNetwork();
			logger.info("Disconnect from network.");
		}

		logger.debug("Navigate back.");
		fNavigationService.navigateBack();
	}

	public void onBootstrapNodeSelected(ActionEvent event) {
		String selectedNode = cbBootstrapNodes.getSelectionModel().getSelectedItem();
		txtBootstrapAddress.setText(selectedNode);
	}

	public void joinNetworkAction(ActionEvent event) {
		clearError();
		boolean inputValid = !validateAll().isError();

		if(inputValid) {
			Task<ResultStatus> task = createJoinTask();
			new Thread(task).start();
		}
	}

	protected ResultStatus joinNetwork(final String address) {
		logger.info("Join network '{}'", address);

		try {
			boolean res = nodeManager.joinNetwork(address);
			if (res) {
				return ResultStatus.ok();
			} else {
				return ResultStatus.error("Could not connect to the network.");
			}
		} catch (UnknownHostException ex) {
			return ResultStatus.error("Could not determine address of host.");
		}
	}

	protected void onJoinSucceeded() {
		logger.info("Join task succeeded: network {} joined.", getBootstrapNode());
		saveJoinConfig();
		resetForm();

//		if (!userConfig.hasRootPath()) {
//			fNavigationService.navigate(ViewNames.SELECT_ROOT_PATH_VIEW);
//		} else {
			fNavigationService.navigate(ViewNames.LOGIN_VIEW);
//		}
	}

	protected void onJoinFailed(ResultStatus result) {
		logger.error("Join task failed: {}", result.getErrorMessage());
		Platform.runLater(() -> {
			uninstallProgressIndicator();
			vboxForm.disableProperty().unbind();
			vboxForm.requestLayout();
			setError(result.getErrorMessage());
		});
	}

	private Task<ResultStatus> createJoinTask() {
		Task<ResultStatus> task = new Task<ResultStatus>() {
			// bootstrap node
			final String address = getBootstrapNode();

			@Override
			public ResultStatus call() {
				return joinNetwork(address);
			}
		};

		task.setOnScheduled(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent event) {
				vboxForm.disableProperty().bind(task.runningProperty());
				installProgressIndicator();
			}
		});

		task.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent event) {
				onJoinFailed(ResultStatus.error("Could not join network."));
			}
		});

		task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent event) {
				ResultStatus result = task.getValue();
				if (result.isOk()) {
					onJoinSucceeded();
				} else {
					onJoinFailed(result);
				}
			}
		});

		return task;
	}

	private ValidationResult validateAll() {
		return (bootstrapValidator.validate() == ValidationResult.OK)
				? ValidationResult.OK : ValidationResult.ERROR;
	}

	private void setError(final String error) {
		if(Platform.isFxApplicationThread()) {
			lblError.setText(error);
		} else {
			Platform.runLater(() -> {
				setError(error); // run again on application thread
			});
		}
	}

	private void clearError() {
		lblError.setText("");
	}

	private String getBootstrapNode() {
		return txtBootstrapAddress.getText().trim();
	}

	@Inject
	public void setBootstrappingNodesFactory(BootstrappingNodesFactory bootstrappingNodesFactory) {
		this.bootstrappingNodesFactory = bootstrappingNodesFactory;
	}
}
