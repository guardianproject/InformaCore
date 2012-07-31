package org.witness.informacam.app;

import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

class MainRouter {
	static interface OnRoutedListener {
		void onRouted();
	}
	
	static boolean show(final Activity activity) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		
		if(!preferences.getBoolean(Settings.Keys.SETTINGS_VIEWED, false)) {
			Log.d(App.LOG, "virgin user, EULA accepted. launching wizard");
			Intent intent = new Intent(activity, WizardActivity.class);
			activity.startActivity(intent);
			return false;
		} else if(preferences.getString(Settings.Keys.CURRENT_LOGIN, "").compareTo(Settings.Login.PW_EXPIRY) == 0) {
			Log.d(App.LOG, "user\'s password expired.  must log in again.");
			Intent intent = new Intent(activity, LoginActivity.class);
			activity.startActivity(intent);
			return false;
		} else {
			((OnRoutedListener) activity).onRouted();
			return true;
		}
	}
	
}
