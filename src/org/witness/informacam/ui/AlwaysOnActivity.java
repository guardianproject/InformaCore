package org.witness.informacam.ui;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.models.j3m.IDCIMDescriptor.IDCIMSerializable;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Camera;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.InformaCamBroadcaster.InformaCamStatusListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class AlwaysOnActivity extends Activity implements InformaCamStatusListener, InformaCamEventListener {
	private final static String LOG = App.Camera.LOG;

	private boolean doInit = true;
	private Intent cameraIntent = null;
	private ComponentName cameraComponent = null;
	private String cameraIntentFlag = Camera.Intents.CAMERA;
	private int storageType = Storage.Type.FILE_SYSTEM;
	private String parentId = null;

	Bundle bundle;
	Handler h = new Handler();

	private InformaCam informaCam;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		informaCam = (InformaCam)getApplication();		
		informaCam.setStatusListener(this);
		informaCam.setEventListener(this);
		
		setContentView(R.layout.activity_informacam_running);
		Button btnStop = (Button)findViewById(R.id.informacam_button);
		btnStop.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View arg0) {
				
				stopMonitoring();
				finish();//on destroy will do the rest
			}
			
		});
		
		if(getIntent().hasExtra(Codes.Extras.MEDIA_PARENT)) {
			parentId = getIntent().getStringExtra(Codes.Extras.MEDIA_PARENT);
		}

		
		try {

			startMonitoring();
			
		} catch(Exception e) {
				
			Toast.makeText(this, "There was an error starting InformaCam", Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	private void startMonitoring () {
		
			//this is for when we don't want InformaCam to launch the camera
			if(informaCam.informaService == null) {
				informaCam.startInforma();
				
			}
			else
			{
				onInformaStart(null);
			}
		
	}

	private void stopMonitoring ()
	{
		if(informaCam.informaService != null && informaCam.informaService.suckersActive()) {
						
			informaCam.informaService.stopAllSuckers();
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
			
		}
	}

	@Override
	public void onInformaCamStart(Intent intent) {
				
		onInformaStart(null);
		
	}

	@Override
	public void onInformaStart(Intent intent) {
		
		informaCam.informaService = InformaService.getInstance();		
		
		if (!informaCam.informaService.suckersActive())
		{
			informaCam.informaService.startAllSuckers();
			informaCam.ioService.startDCIMObserver(AlwaysOnActivity.this, parentId, cameraComponent);
		}
		
		
	}

	@Override
	public void onInformaCamStop(Intent intent) {
		
		
	}

	@Override
	public void onInformaStop(Intent intent) {
		
	}

	@Override
	public void onUpdate(Message message) {
		//Log.d(LOG, "I RECEIVED A MESSAGE (I SHOULDN'T THOUGH)");
		
	}


   @Override
   public void onConfigurationChanged(Configuration newConfig) {
           super.onConfigurationChanged(newConfig);

   }
}
