package org.witness.informacam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models.IUser;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Debug {
	
	public static final String DEBUG_TAG = "ICDEBUG";
	public static final boolean WAIT_FOR_DEBUGGER = false;
	public static final String LOG = "*********************************** " + DEBUG_TAG + "*********************************** ";
	
	public static final boolean DEBUG = true;
	
	public static void testUser_1() {
		InformaCam informaCam = InformaCam.getInstance();
		
		Logger.d(LOG, "TEST USER SETTINGS INABLED:");
		Logger.d(LOG, informaCam.user.asJson().toString());
		
		boolean enc = (Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false);
		Logger.d(LOG, "USER ENC: " + enc);
	}
	
	public static void googledriveTest() {
		InformaCam informaCam = InformaCam.getInstance();
		
		informaCam.resendCredentials(informaCam.installedOrganizations.organizations.get(0));
	}
	
	public static void testFFmpeg() {
		try {
			VideoConstructor vc = new VideoConstructor(InformaCam.getInstance());
			vc.testFFmpeg();
		} catch (FileNotFoundException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
	}
	
	public static void fix_ic_public_folder() {
		java.io.File old_ic_folder = new java.io.File(Storage.EXTERNAL_DIR_V1);
		if(old_ic_folder.exists() && old_ic_folder.isDirectory()) {
			old_ic_folder.renameTo(new java.io.File(Storage.EXTERNAL_DIR));
		}
		
		// go through media manifest and change all InformaCam paths to .InformaCam
		InformaCam informaCam = InformaCam.getInstance();
		for(IMedia m : informaCam.mediaManifest.listMedia) {
			if(m.dcimEntry.fileAsset.path.contains(Storage.DCIM)) {
				try {
					m.dcimEntry.fileAsset.copy(Storage.Type.FILE_SYSTEM, Storage.Type.FILE_SYSTEM, m.rootFolder);
					informaCam.ioService.delete(m.dcimEntry.uri, Storage.Type.CONTENT_RESOLVER);
				} catch(IOException e) {
					Logger.e(LOG, e);
				}
			}
		}
		
		// also, if there is a DCIM path, move the file to .InformaCam
	}
	
	public static void fix_default_asset_encryption() {
		InformaCam informaCam = InformaCam.getInstance();
		
		boolean originalImageHandling = (Boolean) informaCam.user.getPreference("originalImageHandling", false);
		Logger.d(LOG, "USER'S ORIGINAL ENC SETTINGS: " + originalImageHandling);
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(InformaCam.getInstance());
		SharedPreferences.Editor ed = sp.edit();
		ed.putString(IUser.ASSET_ENCRYPTION, originalImageHandling ? "0" : "1");
		ed.commit();
		
		Logger.d(LOG, "USER'S ENC SETTINGS NOW: " + (Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false));
	}
}
