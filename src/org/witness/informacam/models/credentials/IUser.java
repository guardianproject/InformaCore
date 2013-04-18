package org.witness.informacam.models.credentials;

import org.witness.informacam.models.Model;

public class IUser extends Model {
	public boolean hasBaseImage = false;
	public boolean hasPrivateKey = false;
	public boolean hasCompletedWizard = false;
	public boolean hasCredentials = false;

	public boolean isLoggedIn = false;
	public long lastLogIn = 0L;
	public long lastLogOut = 0L;

	public String alias = null;
	public String pgpKeyFingerprint = null;
}
