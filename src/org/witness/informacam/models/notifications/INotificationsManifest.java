package org.witness.informacam.models.notifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.ListAdapterListener;
import org.witness.informacam.utils.Constants.Models;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import android.util.Log;

public class INotificationsManifest extends Model {
	public List<INotification> notifications = new ArrayList<INotification>();
	
	public INotificationsManifest() {
		super();
	}
	
	public List<INotification> listNotifications() {
		return notifications;
	}
	
	public INotification getById(final String _id) {
		Collection<INotification> notifications_ = Collections2.filter(notifications, new Predicate<INotification>() {
			@Override
			public boolean apply(INotification notification) {
				return notification._id.equals(_id);
			}
		});
		
		return notifications_.iterator().next();
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
