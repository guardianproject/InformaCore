package org.witness.informacam.app;

import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.J3M;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Uploader;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferencesActivity extends PreferenceActivity implements OnRoutedListener, OnPreferenceChangeListener {
	ListPreference languagePref, imageHandlingPref, loginCachePref, uploaderModePref;
	CheckBoxPreference useEncryptionPref;
	int langPref, iPref, loginPref, uModePref;
	boolean uPref;
	SharedPreferences sp;
	UploaderService uploaderService = null;

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

		languagePref = (ListPreference) findPreference(Settings.Keys.LANGUAGE);
		imageHandlingPref = (ListPreference) findPreference(Settings.Keys.DEFAULT_IMAGE_HANDLING);
		loginCachePref = (ListPreference) findPreference(Settings.Keys.LOGIN_CACHE_TIME);
		useEncryptionPref = (CheckBoxPreference) findPreference(Settings.Keys.USE_ENCRYPTION);
		uploaderModePref = (ListPreference) findPreference(Settings.Keys.UPLOADER_MODE);

		uploaderModePref.setOnPreferenceChangeListener(this);
		setValues();
		
		/*
		iPref = Integer.parseInt(sp.getString(Settings.Keys.DEFAULT_IMAGE_HANDLING, null)) - 300;
		imageHandlingPref.setValueIndex(iPref);

		loginPref = Integer.parseInt(sp.getString(Settings.Keys.LOGIN_CACHE_TIME, null)) - 200;
		loginCachePref.setValueIndex(loginPref);

		uPref = sp.getBoolean(Settings.Keys.USE_ENCRYPTION, false);
		useEncryptionPref.setValueIndex(uPref ? 0 : 1);
		 */

	}
	
	private void setValues() {
		uploaderModePref.setValueIndex(Settings.Translate(uploaderModePref));
	}

	@Override
	public void onRouted() {
		initPreferences();

	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if(preference.getKey().equals(Settings.Keys.UPLOADER_MODE)) {
			sp.edit().putString(Settings.Keys.UPLOADER_MODE, String.valueOf(newValue)).commit();
			if(uploaderService == null)
				uploaderService = UploaderService.getInstance();
			
			uploaderService.readjustQueue();
		}
		
		return true;
	}
}
