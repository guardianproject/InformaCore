package org.witness.informacam.ui;


import java.util.Iterator;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.models.j3m.IDCIMDescriptor;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.App.Camera;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.InformaCamBroadcaster.InformaCamStatusListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class CameraActivity extends Activity implements InformaCamEventListener, InformaCamStatusListener {
	private final static String LOG = App.Camera.LOG;

	private boolean doInit = true;
	private Intent cameraIntent = null;
	private ComponentName cameraComponent = null;

	private InformaCam informaCam;
	private boolean controlsInforma = true;

	Bundle bundle;
	Handler h = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		h.post(new Runnable() {
			@Override
			public void run() {
				setContentView(R.layout.activity_camera_waiter);
			}
		});
		
		try {
			Iterator<String> i = savedInstanceState.keySet().iterator();
			while(i.hasNext()) {
				String outState = i.next();
				if(outState.equals(Camera.TAG) && savedInstanceState.getBoolean(Camera.TAG)) {
					doInit = false;
				}
			}
		} catch(NullPointerException e) {}

		try {
			informaCam = InformaCam.getInstance(this);
			if(doInit) {
				init();
			}
		} catch(NullPointerException e) {
			Log.e(LOG, "HEY THERE IS NOT INFORMACAM INSTANCE!");
			e.printStackTrace();
			
			startService(new Intent(this, InformaCam.class));
		}


	}

	private void init() {
		List<ResolveInfo> resolveInfo = getPackageManager().queryIntentActivities(new Intent(Actions.CAMERA), 0);

		for(ResolveInfo ri : resolveInfo) {
			String packageName = ri.activityInfo.packageName;
			String name = ri.activityInfo.name;
			
			Log.d(LOG, "found camera app: " + packageName);

			if(Camera.SUPPORTED.indexOf(packageName) >= 0) {
				cameraComponent = new ComponentName(packageName, name);
				break;
			}
		}

		if(resolveInfo.isEmpty() || cameraComponent == null) {
			Toast.makeText(this, getString(R.string.could_not_find_any_camera_activity), Toast.LENGTH_LONG).show();
			setResult(Activity.RESULT_CANCELED);
			finish();
		} else {
			if(informaCam.informaService == null) {
				informaCam.startInforma();
			} else {
				controlsInforma = false;
				onInformaStart(null);
			}
		}
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
	public void onStop() {
		super.onStop();

	
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(Camera.TAG, true);

		super.onSaveInstanceState(outState);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		setResult(Activity.RESULT_CANCELED);

		h.post(new Runnable() {
			@Override
			public void run() {
				try{
					Log.d(LOG, "unregistering dcim observers");
					informaCam.ioService.stopDCIMObserver();
					
					if(controlsInforma) {
						informaCam.stopInforma();
					}
				} catch(NullPointerException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void onUpdate(Message message) {
		byte[] dcimBytes = informaCam.ioService.getBytes(IManifest.DCIM, Type.IOCIPHER);
		if(dcimBytes != null) {
			IDCIMDescriptor dcimDescriptor = new IDCIMDescriptor();
			dcimDescriptor.inflate(dcimBytes);

			Intent result = new Intent().putExtra(Codes.Extras.RETURNED_MEDIA, dcimDescriptor.asJson().toString());
			setResult(Activity.RESULT_OK, result);
			finish();
		} else {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}

	}

	@Override
	public void onInformaCamStart(Intent intent) {
		informaCam = InformaCam.getInstance(this);
		if(doInit) {
			init();
		}
	}
	
	@Override
	public void onInformaStart(Intent intent) {
		informaCam.ioService.startDCIMObserver();
		
		cameraIntent = new Intent(Camera.Intents.CAMERA);
		cameraIntent.setComponent(cameraComponent);
		startActivityForResult(cameraIntent, Codes.Routes.IMAGE_CAPTURE);
		
	}

	@Override
	public void onInformaCamStop(Intent intent) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInformaStop(Intent intent) {
		// TODO Auto-generated method stub
		
	}

	
}
