package org.witness.informacam.models;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.models.notifications.INotification;

public class INotificationsManifest extends Model {
	public List<INotification> notifications = new ArrayList<INotification>();
}
