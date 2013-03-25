package org.witness.informacam.informa;

import org.witness.informacam.utils.Constants.App;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class InformaService extends Service {
	private final IBinder binder = new LocalBinder();
	private static InformaService informaService;
	
	private final static String LOG = App.Informa.LOG;
	
	public class LocalBinder extends Binder {
		public InformaService getService() {
			return InformaService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		
		informaService = this;
	}
	
	public InformaService getInstance() {
		return informaService;
	}

}
