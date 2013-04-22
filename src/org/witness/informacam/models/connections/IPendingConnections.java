package org.witness.informacam.models.connections;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.models.Model;

public class IPendingConnections extends Model {
	public List<IConnection> queue = new ArrayList<IConnection>();
	
	public IConnection getById(long id) {
		for(IConnection connection : queue) {
			if(connection._id == id) {
				return connection;
			}
		}
		
		return null;
	}
}
