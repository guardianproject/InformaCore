package org.witness.informacam.models.notifications;

import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.App.Storage.Type;

public class INotification extends Model {
	public long timestamp = System.currentTimeMillis();
	public String label = null;
	public String content = null;
	public String from = null;
	public String icon = null;
	public int iconSource = Type.IOCIPHER;
	public int type = 0;
	
}
