package org.witness.informacam.ui;

import java.util.Iterator;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.models.j3m.IDCIMDescriptor.IDCIMSerializable;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Camera;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.Logger;
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

public class CameraActivity extends Activity implements InformaCamStatusListener, InformaCamEventListener {
	private final static String LOG = App.Camera.LOG;

	private boolean doInit = true;
	private Intent cameraIntent = null;
	private ComponentName cameraComponent = null;
	private String cameraIntentFlag = Camera.Intents.CAMERA;

	private boolean controlsInforma = true;
	private String parentId = null;

	Bundle bundle;
	Handler h = new Handler();

	private InformaCam informaCam;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_camera_waiter);
		
		informaCam = (InformaCam)getApplication();		
		
		h.post(new Runnable() {
			@Override
			public void run() {
				setContentView(R.layout.activity_camera_waiter);
			}
		});
		
		if(getIntent().hasExtra(Codes.Extras.MEDIA_PARENT)) {
			parentId = getIntent().getStringExtra(Codes.Extras.MEDIA_PARENT);
			Logger.d(LOG, "TO PARENT " + parentId);
		}

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
			
			if(doInit) {
				if(getIntent().hasExtra(Codes.Extras.CAMERA_TYPE)) {
					int cameraType = getIntent().getIntExtra(Codes.Extras.CAMERA_TYPE, -1);
					switch(cameraType) {
					case Camera.Type.CAMERA:
						cameraIntentFlag = Camera.Intents.CAMERA_SIMPLE;
						break;
					case Camera.Type.CAMCORDER:
						cameraIntentFlag = Camera.Intents.CAMCORDER;
						break;
					}

				}

				init();
			}
		} catch(NullPointerException e) {
			e.printStackTrace();

			startService(new Intent(this, InformaCam.class));
		}
	}

	private void init() {
		List<ResolveInfo> resolveInfo = getPackageManager().queryIntentActivities(new Intent(cameraIntentFlag), 0);

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
		informaCam.setStatusListener(this);
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

		Logger.d(LOG, "COMING BACK FROM ON-BOARD CAMERA");
		if(controlsInforma) {
			Logger.d(LOG, "ALSO, I CONTROL INFORMA");
			
			informaCam.ioService.stopDCIMObserver();
			informaCam.stopInforma();
			
			IDCIMSerializable dcimDescriptor = informaCam.ioService.getDCIMDescriptor().asDescriptor();
			if(dcimDescriptor.dcimList.size() > 0) {
				Intent result = new Intent().putExtra(Codes.Extras.RETURNED_MEDIA, dcimDescriptor);
				setResult(Activity.RESULT_OK, result);
			} else {
				setResult(Activity.RESULT_CANCELED);
			}
			finish();
			
		} else {
			onInformaStop(null);
		}
	}

	@Override
	public void onInformaCamStart(Intent intent) {
		
		if(doInit) {
			init();
		}
	}

	@Override
	public void onInformaStart(Intent intent) {
		
		informaCam.informaService = InformaService.getInstance();
		
		h.post(new Runnable() {
			@Override
			public void run() {
				informaCam.ioService.startDCIMObserver(CameraActivity.this, parentId, cameraComponent);
			}
		});
		
		cameraIntent = new Intent(cameraIntentFlag);
		cameraIntent.setComponent(cameraComponent);
		startActivityForResult(cameraIntent, Codes.Routes.IMAGE_CAPTURE);
	}

	@Override
	public void onInformaCamStop(Intent intent) {}

	@Override
	public void onInformaStop(Intent intent) {
		
	}

	@Override
	public void onUpdate(Message message) {
		Log.d(LOG, "I RECEIVED A MESSAGE (I SHOULDN'T THOUGH)");
		
	}


}
