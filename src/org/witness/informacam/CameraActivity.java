package org.witness.informacam;


import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

public class CameraActivity extends Activity {
	Intent init;
	private final static String LOG = App.Camera.LOG;
	private String packageName;
	
	private InformaCam informaCam;
	Handler h = new Handler();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		packageName = getClass().getName();
		
		Log.d(LOG, "hello " + packageName);
		
		try {
			informaCam = InformaCam.getInstance();
			informaCam.associateActivity(CameraActivity.this);
			
			Log.d(LOG, "no we have informa so it is ok");
			init();
		} catch(NullPointerException e) {
			startService(new Intent(this, org.witness.informacam.InformaCam.class));
			h.postDelayed(new Runnable() {
				@Override
				public void run() {
					informaCam = InformaCam.getInstance();
					informaCam.associateActivity(CameraActivity.this);
					
					new Thread(new Runnable() {
						@Override
						public void run() {
							informaCam.startup();
						}
					}).start();
					
				}
			}, 1500);
		}
	}
	
	private void init() {
		Intent launch = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		startActivityForResult(launch, Codes.Routes.IMAGE_CAPTURE);
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onPause() {
		super.onPause();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if(resultCode == Activity.RESULT_CANCELED) {
			finish();
		}
		
		
	}
}
