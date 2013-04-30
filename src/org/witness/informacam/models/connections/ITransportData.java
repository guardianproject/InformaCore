package org.witness.informacam.models.connections;

import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.App.Storage.Type;

public class ITransportData extends Model {
	public int source = Type.IOCIPHER;
	public String key = null;
	public String entityName = null;
	public int totalBytes = 0;
	public int[] byteRange = new int[] {0, -1};
	public boolean isWholeUpload = false;
	
}
