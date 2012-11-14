package org.witness.informacam.app;

import org.witness.informacam.R;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.utils.Constants.Settings;

import com.xtralogic.android.logcollector.SendLogActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

class MainRouter {
	static interface OnRoutedListener {
		void onRouted();
	}
	
	static boolean show(final Activity activity) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		
		if(!preferences.getBoolean(Settings.Keys.SETTINGS_VIEWED, false)) {
			Intent intent = new Intent(activity, WizardActivity.class);
			activity.startActivity(intent);
			return false;
		} else if(preferences.getString(Settings.Keys.CURRENT_LOGIN, "").compareTo(Settings.Login.PW_EXPIRY) == 0) {
			Intent intent = new Intent(activity, LoginActivity.class);
			activity.startActivity(intent);
			return false;
		} else {
			if(LoginActivity.validatePassword(activity, preferences.getString(Settings.Keys.CURRENT_LOGIN, "")))
				((OnRoutedListener) activity).onRouted();
			else
				Toast.makeText(activity, activity.getString(R.string.error_password_mismatch), Toast.LENGTH_LONG).show();
			return true;
		}
	}
	
	static void launchAbout(Activity activity) {
    	Intent intent = new Intent(activity, AboutActivity.class);
    	activity.startActivity(intent);
    }
	
	static void launchPreferences(Activity activity) {
    	Intent intent = new Intent(activity, PreferencesActivity.class);
    	activity.startActivity(intent);
    }
	
	static void launchSendLog(Activity activity) {
    	Intent intent = new Intent(activity, SendLogActivity.class);
    	activity.startActivity(intent);
    }
    
    static void launchKnowledgebase(Activity activity) {
    	Intent intent = new Intent(activity, KnowledgebaseActivity.class);
    	activity.startActivity(intent);
    }
    
    static void doLogout(Activity activity) {
    	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
    	preferences.edit().putString(Settings.Keys.CURRENT_LOGIN, Settings.Login.PW_EXPIRY).commit();
	}
    
    static void exportDeviceKey(Activity activity) {
		Intent export = new Intent()
			.setAction(Intent.ACTION_SEND)
			.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(KeyUtility.exportPublicKey()))
			.setType("*/*");
		activity.startActivity(export);
		
	}
}
