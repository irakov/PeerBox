package org.peerbox.notifications;

import org.peerbox.presenter.tray.TrayException;

import net.engio.mbassy.listener.Handler;


public interface ITrayNotifications {
	
	@Handler
	public void showInformation(InformationNotification in);
	
	@Handler
	public void showFileEvents(AggregatedFileEventStatus event);

	void showSuccessIcon() throws TrayException;
	
}
