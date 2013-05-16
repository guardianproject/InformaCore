package org.witness.informacam.models.notifications;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.ListAdapterListener;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class INotificationsManifest extends Model {
	public List<INotification> notifications = null;
	
	public INotificationsManifest() {
		super();
	}
	
	public INotification getById(String _id) {
		if(notifications != null) {
			for(INotification notification : notifications) {
				if(notification._id.equals(_id)) {
					return notification;
				}
			}
		}
		
		return null;
	}
	
	public void sortBy(int order) {
		if(notifications == null || notifications.size() == 0) {
			return;
		}
		
		switch(order) {
		case Models.INotificationManifest.Sort.DATE_DESC:
			Comparator<INotification> DateDesc = new Comparator<INotification>() {

				@Override
				public int compare(INotification n1, INotification n2) {
					return n1.timestamp > n2.timestamp ? -1 : (n1==n2 ? 0 : 1);
				}
				
			};
			Collections.sort(notifications, DateDesc);
			break;
		case Models.INotificationManifest.Sort.DATE_ASC:
			Comparator<INotification> DateAsc = new Comparator<INotification>() {

				@Override
				public int compare(INotification n1, INotification n2) {
					return n1.timestamp < n2.timestamp ? -1 : (n1==n2 ? 0 : 1);
				}
				
			};
			Collections.sort(notifications, DateAsc);
			break;
		}
	}
}
