package org.witness.informacam.models.notifications;

import org.witness.informacam.models.Model;

public class INotification extends Model {
	public long timestamp = System.currentTimeMillis();
	public String label = null;
	public String content = null;
	public String from = null;
	public int type = 0;
	
}
