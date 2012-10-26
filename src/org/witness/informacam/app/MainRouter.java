package org.witness.informacam.app;

import org.witness.informacam.R;
import org.witness.informacam.utils.Constants.Settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
}
