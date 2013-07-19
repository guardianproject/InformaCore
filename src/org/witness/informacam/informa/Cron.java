package org.witness.informacam.informa;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class Cron extends Service {
	private final static String LOG = App.Background.LOG;
	
	InformaCam informaCam = null;
	
	/*
	 * requires:
	 * 
	 *  access to iocipher
	 *  access to informa
	 *  
	 */
	
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
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
