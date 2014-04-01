package org.witness.informacam.models.credentials;

import java.io.Serializable;

import org.json.JSONObject;
import org.witness.informacam.InformaCam;
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
	public IPreferences preferences = null; 
	
	public IUser() {
		super();
	}
	
	public IUser(JSONObject user) {
		super();
		inflate(user);
	}
	
	public void setIsLoggedIn(boolean isLoggedIn) {
		this.isLoggedIn = isLoggedIn;
		save();
	}
	
	public void setHasBaseImage(boolean hasBaseImage) {
		this.hasBaseImage = hasBaseImage;
		save();
	}
	
	public void setHasCompletedWizard(boolean hasCompletedWizard) {
		this.hasCompletedWizard = hasCompletedWizard;
		save();
	}
	
	public void setHasPrivateKey(boolean hasPrivateKey) {
		this.hasPrivateKey = hasPrivateKey;
		save();
	}
	
	public void setHasCredentials(boolean hasCredentials) {
		this.hasCredentials = hasCredentials;
		save();
	}
	
	public boolean save() {
		return InformaCam.getInstance().saveState(this);
	}
}