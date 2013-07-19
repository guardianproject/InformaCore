package org.witness.informacam.models.credentials;

import java.io.Serializable;

import org.json.JSONObject;
import org.witness.informacam.models.Model;

@SuppressWarnings("serial")
public class IUser extends Model implements Serializable {
	public boolean hasBaseImage = false;
	public boolean hasPrivateKey = false;
	public boolean hasCompletedWizard = false;
	public boolean hasCredentials = false;

	public boolean isLoggedIn = false;
	public long lastLogIn = 0L;
	public long lastLogOut = 0L;

	public String alias = null;
	public String email = null;
	public String pgpKeyFingerprint = null;
	
	public boolean isInOfflineMode = false;
	
	public IUser() {
		super();
	}
	
	public IUser(JSONObject user) {
		super();
		inflate(user);
	}
}
