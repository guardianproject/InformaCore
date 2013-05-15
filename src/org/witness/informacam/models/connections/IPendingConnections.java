package org.witness.informacam.models.connections;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;

import android.util.Log;

public class IPendingConnections extends Model {
	public List<IConnection> queue = new ArrayList<IConnection>();
	
	public IConnection getById(long id) {
		synchronized(queue) {
			for(IConnection connection : queue) {
				if(connection._id == id) {
					return connection;
				}
			}
		}
		
		return null;
	}
	
	public void remove(IConnection connection) {
		synchronized(queue) {
			queue.remove(connection);
			InformaCam.getInstance().saveState(this);
		}
	}
	
	public void add(IConnection connection) {
		synchronized(queue) {
			Log.d(LOG, "Adding a new connection.\n" + connection.asJson().toString());
			queue.add(connection);
			InformaCam.getInstance().saveState(this);
		}
	}
}
