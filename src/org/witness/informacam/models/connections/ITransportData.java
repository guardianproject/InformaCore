package org.witness.informacam.models.connections;

import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.App.Storage.Type;

public class ITransportData extends Model {
	public int source = Type.IOCIPHER;
	public String key = null;
	public String entityName = null;
	
}
