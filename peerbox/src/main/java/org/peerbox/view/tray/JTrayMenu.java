package org.peerbox.view.tray;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionListener;
import java.io.IOException;

import org.peerbox.presenter.tray.TrayActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JTrayMenu {

	private static final Logger logger = LoggerFactory.getLogger(JTrayMenu.class);

	private PopupMenu root;
	private TrayActionHandler actionHandler;

	public JTrayMenu(TrayActionHandler actionHandler) {
		this.actionHandler = actionHandler;
	}

	public PopupMenu create() {
		root = new PopupMenu();

		root.add(createRootFolderMenu());
		// root.add(createRecentFilesMenu()); // TODO implement it...
		root.addSeparator();
		root.add(createSettingsMenu());
		root.addSeparator();
		root.add(createQuitMenu());

		return root;
	}

	private MenuItem createRootFolderMenu() {
		MenuItem rootItem = new MenuItem("Open Folder");
		rootItem.addActionListener(createRootFolderListener());
		return rootItem;
	}

	private ActionListener createRootFolderListener() {
		ActionListener closeListener = new ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				try {
					actionHandler.openRootFolder();
				} catch (IOException ex) {
					logger.debug("Could not open folder.", ex);
					logger.error("Could not open root folder.");
				}
			}
		};
		return closeListener;
	}

	private MenuItem createSettingsMenu() {
		MenuItem settingsItem = new MenuItem("Settings");
		settingsItem.addActionListener(createSettingsListener());
		return settingsItem;
	}

	private ActionListener createSettingsListener() {
		ActionListener closeListener = new ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				actionHandler.showSettings();
			}
		};
		return closeListener;
	}

	private MenuItem createQuitMenu() {
		MenuItem closeItem = new MenuItem("Quit");
		closeItem.addActionListener(createQuitListener());
		return closeItem;
	}

	private ActionListener createQuitListener() {
		ActionListener closeListener = new ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				actionHandler.quit();
			}
		};
		return closeListener;
	}
}
