package org.witness.informacam.models.connections;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;

import android.util.Log;

public class IPendingConnections extends Model {
	public List<IConnection> queue = new ArrayList<IConnection>();
	public List<IConnection> removal = null;
	public List<IConnection> addition = null;

	public IConnection getById(long id) {
		for(IConnection connection : queue) {
			if(connection._id == id) {
				return connection;
			}

		}

		return null;
	}
	
	public void save() {
		Log.d(LOG, "queue size: " + queue.size());
		
		if(removal != null && removal.size() > 0) {
			for(IConnection connection : removal) {
				Log.d(LOG, "removing connection " + connection._id);
				queue.remove(connection);
			}
			
			removal = null;
		}
		
		if(addition != null && addition.size() > 0) {
			queue.addAll(addition);
			
			addition = null;
		}
		
		Log.d(LOG, "queue new size: " + queue.size());
		InformaCam.getInstance().saveState(this);
		InformaCam.getInstance().uploaderService.pendingConnections = this;
	}

	public void remove(IConnection connection) {
		if(removal == null) {
			removal = new ArrayList<IConnection>();
		}
		
		Log.d(LOG, "SHOULD REMOVE CONNECTION " + connection._id);
		connection.slatedForRemoval = true;
		removal.add(connection);
	}

	public void add(IConnection connection) {
		Log.d(LOG, "Adding a new connection.\n" + connection.asJson().toString());
		if(addition == null) {
			addition = new ArrayList<IConnection>();
		}
		
		addition.add(connection);
	}
}
