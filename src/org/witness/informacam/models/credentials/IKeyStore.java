package org.witness.informacam.models.credentials;

import org.witness.informacam.models.Model;

public class IKeyStore extends Model {
	public String path = null;
	public String password = null;
	public long lastModified = 0L;
	
	public IKeyStore() {}
}
