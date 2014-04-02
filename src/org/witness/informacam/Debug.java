package org.witness.informacam;

import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models.IUser;

public class Debug {
	
	public static final String DEBUG_TAG = "ICDEBUG";
	public static final boolean WAIT_FOR_DEBUGGER = false;
	public static final String LOG = "*********************************** " + DEBUG_TAG + "*********************************** ";
	
	public static final boolean DEBUG = true;
	
	public static void testUser_1() {
		InformaCam informaCam = InformaCam.getInstance();
		informaCam.user.isInOfflineMode = true;
		
		Logger.d(LOG, "TEST USER SETTINGS INABLED:");
		Logger.d(LOG, informaCam.user.asJson().toString());
		
		boolean enc = (Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false);
		Logger.d(LOG, "USER ENC: " + enc);
		
		//informaCam.mediaManifest.listMedia.clear();
		//informaCam.mediaManifest.save();
	}

}
