package org.witness.informacam.app;

import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferencesActivity extends PreferenceActivity implements OnRoutedListener {
	ListPreference languagePref, imageHandlingPref, loginCachePref, useEncryptionPref;
	int langPref, iPref, loginPref;
	boolean uPref;
	SharedPreferences sp;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		MainRouter.show(this);
	}
	
	@SuppressWarnings("deprecation")
	private void initPreferences() {
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		languagePref = (ListPreference) findPreference("obscura.language");
		imageHandlingPref = (ListPreference) findPreference("informa.DefaultImageHandling");
		loginCachePref = (ListPreference) findPreference("informa.PasswordCacheTimeout");
		useEncryptionPref = (ListPreference) findPreference("informa.UseEncryption");
		
		Log.d(App.Preferences.LOG, "image handling: " + sp.getString(Settings.Keys.DEFAULT_IMAGE_HANDLING, null));
		Log.d(App.Preferences.LOG, "login cache: " + sp.getString(Settings.Keys.LOGIN_CACHE_TIME, null));
		
		iPref = Integer.parseInt(sp.getString(Settings.Keys.DEFAULT_IMAGE_HANDLING, null)) - 300;
		imageHandlingPref.setValueIndex(iPref);
		
		loginPref = Integer.parseInt(sp.getString(Settings.Keys.LOGIN_CACHE_TIME, null)) - 200;
		loginCachePref.setValueIndex(loginPref);
		
		uPref = sp.getBoolean(Settings.Keys.USE_ENCRYPTION, false);
		useEncryptionPref.setValueIndex(uPref ? 0 : 1);
		
	}

	@Override
	public void onRouted() {
		initPreferences();
		
	}
}
