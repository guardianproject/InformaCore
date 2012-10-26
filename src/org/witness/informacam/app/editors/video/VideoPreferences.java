/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */

package org.witness.informacam.app.editors.video;

import org.witness.informacam.R;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.VideoEditor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;


public class VideoPreferences extends PreferenceActivity  {
	int dFrameRate, dBitrate, dWidth, dHeight;
	SharedPreferences sp;
	ListPreference dimensionPref, bitratePref, frameratePref;
	
	@SuppressWarnings("deprecation")
	protected void onCreate(Bundle savedInstanceState) {
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.videopreferences);
		dFrameRate = sp.getInt(VideoEditor.Preferences.FRAME_RATE, VideoEditor.Preferences.DEFAULT_OUT_FPS);
		dBitrate = sp.getInt(VideoEditor.Preferences.BIT_RATE, VideoEditor.Preferences.DEFAULT_OUT_RATE);
		dWidth = sp.getInt(VideoEditor.Preferences.WIDTH, VideoEditor.Preferences.DEFAULT_OUT_WIDTH);
		dHeight = sp.getInt(VideoEditor.Preferences.HEIGHT, VideoEditor.Preferences.DEFAULT_OUT_HEIGHT);
		
		final Bundle vidDefaults = getIntent().getExtras();
		try {
			dFrameRate = vidDefaults.getInt(App.VideoEditor.Preferences.FRAME_RATE);
			dBitrate = vidDefaults.getInt(App.VideoEditor.Preferences.BIT_RATE);
			dWidth = vidDefaults.getInt(App.VideoEditor.Preferences.WIDTH);
			dHeight = vidDefaults.getInt(App.VideoEditor.Preferences.HEIGHT);
		} catch(NullPointerException e) {}
		
		dimensionPref = (ListPreference) findPreference("pref_out_vdimensions");
		
		int dPref;
		try {
			dPref = sp.getInt(VideoEditor.Preferences.DIMENSIONS, VideoEditor.Preferences.Dimensions.ORIGINAL) - 500;
		} catch(ClassCastException e) {
			dPref = Integer.parseInt(sp.getString(VideoEditor.Preferences.DIMENSIONS, String.valueOf(VideoEditor.Preferences.Dimensions.ORIGINAL))) - 500;
		}
		
		dimensionPref.setValueIndex (dPref);
		dimensionPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int newDims = Integer.parseInt(String.valueOf(newValue));
				switch(newDims) {
				case VideoEditor.Preferences.Dimensions.HD:
					dWidth = 1280;
					dHeight = 780;
					break;
				case VideoEditor.Preferences.Dimensions.SD_H:
					dWidth = 480;
					dHeight = 360;
					break;
				case VideoEditor.Preferences.Dimensions.SD_L:
					dWidth = 176;
					dHeight = 144;
					break;
				case VideoEditor.Preferences.Dimensions.ORIGINAL:
					dWidth = vidDefaults.getInt(App.VideoEditor.Preferences.WIDTH);
					dHeight = vidDefaults.getInt(App.VideoEditor.Preferences.HEIGHT);
					break;
				}
				
				sp.edit().putInt(VideoEditor.Preferences.WIDTH, dWidth).commit();
				sp.edit().putInt(VideoEditor.Preferences.HEIGHT, dHeight).commit();
				
				Log.d(App.LOG, "BTW dimension prefs: " + dWidth + ", " + dHeight);
				
				return true;
			}
			
		});
		
		int bPref;
		try {
			bPref = sp.getInt(VideoEditor.Preferences.BIT_RATE, VideoEditor.Preferences.DEFAULT_OUT_RATE);
		} catch(ClassCastException e) {
			bPref = Integer.parseInt(sp.getString(VideoEditor.Preferences.BIT_RATE, String.valueOf(VideoEditor.Preferences.DEFAULT_OUT_RATE)));
		}
		
		bitratePref = (ListPreference) findPreference("pref_out_rate");
		bitratePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				sp.edit().putInt(VideoEditor.Preferences.BIT_RATE, Integer.parseInt(String.valueOf(newValue)));
				return true;
			}
			
		});
		
	}
	
	
	@Override
	protected void onResume() {
	
		super.onResume();
	
		
		
	};
	
	
	
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onStop()
	 */
	@Override
	protected void onStop() {
		super.onStop();
		
		//Log.d(getClass().getName(),"Exiting Preferences");
	}

	
}
