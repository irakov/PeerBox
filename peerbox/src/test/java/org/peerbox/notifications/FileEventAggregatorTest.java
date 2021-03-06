package org.peerbox.notifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.engio.mbassy.listener.Handler;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.peerbox.app.manager.file.RemoteFileDeletedMessage;
import org.peerbox.app.manager.file.RemoteFileMovedMessage;
import org.peerbox.app.manager.file.RemoteFileAddedMessage;
import org.peerbox.app.manager.file.RemoteFileUpdatedMessage;
import org.peerbox.events.MessageBus;
import org.peerbox.presenter.settings.synchronization.FileHelper;
import org.peerbox.presenter.tray.TrayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileEventAggregatorTest  {

	private static final Logger logger = LoggerFactory.getLogger(FileEventAggregatorTest.class);
	
	private FileEventAggregator aggregator;
	private MessageBus messageBus = new MessageBus();
	
	private TrayNotificationsStub notifications;
	
	private static Random rnd;
	
	@BeforeClass
	public static void setup() {
		rnd = new Random(7);
	}
	
	@Before
	public void initialization() {
		notifications = new TrayNotificationsStub();
		aggregator = new FileEventAggregator(messageBus);
		messageBus.subscribe(aggregator);
		messageBus.subscribe(notifications);
	}
	
	private AggregatedFileEventStatus sendEvents(long timeToSendEvents) throws InterruptedException {
		int totalAdded = 0;
		int totalModified = 0;
		int totalDeleted = 0;
		int totalMoved = 0;
		
		long startTime = System.currentTimeMillis();
		int numFile = 0;
		while(System.currentTimeMillis() < (startTime + timeToSendEvents)) {
			Path p = Paths.get(String.valueOf(numFile));
			Thread.sleep(rnd.nextInt(5));
			FileHelper file = new FileHelper(p, true);
			FileHelper dstFile = new FileHelper(Paths.get("my/path"), true);
			switch (rnd.nextInt(4)) {
				case 0:
					
					aggregator.onFileAdded(new RemoteFileAddedMessage(file));
					++totalAdded;
					break;
				case 1:
					aggregator.onFileUpdated(new RemoteFileUpdatedMessage(file));
					++totalModified;
					break;
				case 2:
					aggregator.onFileDeleted(new RemoteFileDeletedMessage(file));
					++totalDeleted;
					break;
				case 3:
					aggregator.onFileMoved(new RemoteFileMovedMessage(file, dstFile));
					++totalMoved;
				default:
					break;
			}
			++numFile;
		}
		
		return new AggregatedFileEventStatus(totalAdded, totalModified, totalDeleted, totalMoved);
	}
	
	@Test
	public void testRandomFileEventAggregation() throws InterruptedException {
		
		long timeToSendEvents = (long) (1.5 * FileEventAggregator.AGGREGATION_TIMESPAN);
		AggregatedFileEventStatus sent = sendEvents(timeToSendEvents);
		
		// wait some time to complete the aggregation
		Thread.sleep((long) (1.1*FileEventAggregator.AGGREGATION_TIMESPAN));
		logger.info("Expected - Added: {}, Modified: {}, Deleted: {}", 
				sent.getNumFilesAdded(), sent.getNumFilesModified(), sent.getNumFilesDeleted());
		
		List<AggregatedFileEventStatus> events = notifications.getAggregatedFileEvents();
		assertFalse(events.isEmpty());
		int receivedAdds = 0;
		int receivedModifieds = 0;
		int receivedDeletes = 0;
		for(AggregatedFileEventStatus e : events) {
			receivedAdds += e.getNumFilesAdded();
			receivedModifieds += e.getNumFilesModified();
			receivedDeletes += e.getNumFilesDeleted();
		}
		logger.info("Actual - Added: {}, Modified: {}, Deleted: {}", 
				receivedAdds, receivedModifieds, receivedDeletes);
		
		assertTrue(receivedAdds == sent.getNumFilesAdded());
		assertTrue(receivedModifieds == sent.getNumFilesModified());
		assertTrue(receivedDeletes == sent.getNumFilesDeleted());
	}
	
	
	@Test
	public void testRandomFileEventAggregationWaitTime() throws InterruptedException {
		
		long timeToSendEvents = (long) (1.5 * FileEventAggregator.AGGREGATION_TIMESPAN);
		AggregatedFileEventStatus sent = sendEvents(timeToSendEvents);
		
		// wait not enough long to complete the aggregation -> Should NOT receive all events!
		Thread.sleep((long) (0.1*FileEventAggregator.AGGREGATION_TIMESPAN));
		logger.info("Expected - Added: {}, Modified: {}, Deleted: {}", 
				sent.getNumFilesAdded(), sent.getNumFilesModified(), sent.getNumFilesDeleted());
		
		List<AggregatedFileEventStatus> events = notifications.getAggregatedFileEvents();
		assertFalse(events.isEmpty());
		int receivedAdds = 0;
		int receivedModifieds = 0;
		int receivedDeletes = 0;
		for(AggregatedFileEventStatus e : events) {
			receivedAdds += e.getNumFilesAdded();
			receivedModifieds += e.getNumFilesModified();
			receivedDeletes += e.getNumFilesDeleted();
		}
		logger.info("Actual - Added: {}, Modified: {}, Deleted: {}", 
				receivedAdds, receivedModifieds, receivedDeletes);
		
		assertTrue(receivedAdds < sent.getNumFilesAdded());
		assertTrue(receivedModifieds < sent.getNumFilesModified());
		assertTrue(receivedDeletes < sent.getNumFilesDeleted());
	}
	
	private class TrayNotificationsStub implements ITrayNotifications {
		
		private List<InformationNotification> informationNotification;
		private List<AggregatedFileEventStatus> aggregatedFileEvents;
		
		public TrayNotificationsStub() {
			informationNotification = new ArrayList<InformationNotification>();
			aggregatedFileEvents = new ArrayList<AggregatedFileEventStatus>();
		}
		
		
		public List<InformationNotification> getInformationNotification() {
			return informationNotification;
		}

		public List<AggregatedFileEventStatus> getAggregatedFileEvents() {
			return aggregatedFileEvents;
		}

		@Override
		@Handler
		public void showInformation(InformationNotification in) {
			informationNotification.add(in);
		}

		@Override
		@Handler
		public void showFileEvents(AggregatedFileEventStatus event) {
			aggregatedFileEvents.add(event);
		}


		@Override
		public void showSuccessIcon() throws TrayException {
			// TODO Auto-generated method stub
			
		}
	}
}
