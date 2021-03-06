package org.peerbox.presenter.settings;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import org.apache.commons.io.FileUtils;
import org.peerbox.app.config.UserConfig;
import org.peerbox.presenter.validation.SelectRootPathUtils;
import org.peerbox.view.converter.EnabledDisabledStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class Account implements Initializable {
	
	private static final Logger logger = LoggerFactory.getLogger(Account.class);
	
	private UserConfig userConfig;
	
	@FXML
	private TextField txtUsername;
	@FXML
	private PasswordField txtPassword;
	@FXML 
	private PasswordField txtPin;
	@FXML
	private Button btnDisableAutoLogin;
	@FXML
	private CheckBox chbAutoLogin;
	
	@FXML
	private CheckBox chbTrayNotification;
	
	@FXML
	private TextField txtRootPath;
	@FXML 
	private Button btnMoveRootPath;
	
	@Inject
	public Account(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		chbAutoLogin.textProperty().bindBidirectional(chbAutoLogin.selectedProperty(), 
				new EnabledDisabledStringConverter());
		reset();		
	}

	private void reset() {
		if(userConfig.hasUsername()) {
			txtUsername.setText(userConfig.getUsername());
		}
		if(userConfig.hasPassword()) {
			txtPassword.setText(userConfig.getPassword());
		}
		if(userConfig.hasPin()) {
			txtPin.setText(userConfig.getPin());
		}
		chbAutoLogin.setSelected(userConfig.isAutoLoginEnabled());
		if(userConfig.hasRootPath()) {
			txtRootPath.setText(userConfig.getRootPath().toString());
		}
		chbTrayNotification.setSelected(userConfig.isTrayNotificationEnabled());
		
	}
	
	
	public void disableAutoLoginAction(ActionEvent event) {
		try {
			chbAutoLogin.setSelected(false);
			userConfig.setAutoLogin(false);
		} catch (IOException e) {
			logger.warn("Could not store user configuration.");
		}
	}
	
	public void disableTrayNotificationAction(ActionEvent event) {
		try {
			chbTrayNotification.setSelected(!userConfig.isTrayNotificationEnabled());
			userConfig.setTrayNotification(!userConfig.isTrayNotificationEnabled());
		} catch (IOException e) {
			logger.warn("Was not possible to change Tray Notification status.");
		}
	}
	
	public void moveRootPathAction(ActionEvent event) {
		try {
			File currentPath = userConfig.getRootPath().toFile();
			String newPathParentStr = currentPath.toString();
			
			Window toOpenDialog = btnMoveRootPath.getScene().getWindow();
			newPathParentStr = SelectRootPathUtils.showDirectoryChooser(newPathParentStr, toOpenDialog);

			File newPathParent = new File(newPathParentStr);
			File newPath = new File(newPathParent, currentPath.getName());
			boolean yes = SelectRootPathUtils.confirmMoveDirectoryDialog(newPath);
			if(yes) {
				FileUtils.moveDirectoryToDirectory(currentPath, newPathParent, false);
				
				txtRootPath.setText(newPath.toString());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
