package org.witness.informacam.ui;

import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class LaunchActivity extends Activity {
	private final static String LOG = App.LOG;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(LOG, "HI");
		//startService(new Intent(this, InformaCam.class));
		sendBroadcast(new Intent(Actions.INFORMACAM_START));
	}
}
