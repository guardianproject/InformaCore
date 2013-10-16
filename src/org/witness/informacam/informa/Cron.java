package org.witness.informacam.informa;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.InformaCamBroadcaster.InformaCamStatusListener;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class Cron extends Service implements InformaCamEventListener, InformaCamStatusListener {
	private final static String LOG = App.Background.LOG;
	
	InformaCam informaCam = null;
	
	@Override
	public void onCreate() {
		Toast.makeText(this, "Cron.onCreate()", Toast.LENGTH_LONG).show();
		Log.d(LOG, "Cron.onCreate()");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		Toast.makeText(this, "Cron.onStartCommand()", Toast.LENGTH_LONG).show();
		Log.d(LOG, "Cron.onStartCommand()");
		
		informaCam = (InformaCam)getApplication();
		
		informaCam.setEventListener(this);
		informaCam.setStatusListener(this);
		
		informaCam.startInforma();
		
		return START_STICKY;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		Toast.makeText(this, "Cron.onUnbind()", Toast.LENGTH_LONG).show();
		Log.d(LOG, "Cron.onUnbind()");
		return super.onUnbind(intent);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Toast.makeText(this, "Cron.onDestroy()", Toast.LENGTH_LONG).show();
		Log.d(LOG, "Cron.onDestroy()");
		
		informaCam.stopInforma();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onInformaCamStart(Intent intent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onInformaCamStop(Intent intent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onInformaStop(Intent intent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onInformaStart(Intent intent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUpdate(Message message) {
		// TODO Auto-generated method stub
		
	}

}
