package org.witness.informacam.informa;

import java.util.List;

import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.models.ISuckerCache;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.LogPack;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class InformaService extends Service implements SuckerCacheListener {
	private final IBinder binder = new LocalBinder();
	private static InformaService informaService;
	
	private long timeOffset = 0L;
	
	SensorLogger<GeoSucker> _geo;
	SensorLogger<PhoneSucker> _phone;
	SensorLogger<AccelerometerSucker> _acc;
	ISuckerCache cache = new ISuckerCache();
	
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
		
		// TODO: start suckers
		
		// TODO: resolve time
		
		informaService = this;
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE).setAction(Actions.ASSOCIATE_SERVICE));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		// TODO: save stuff
		
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE).setAction(Actions.DISASSOCIATE_SERVICE));
	}
	
	public static InformaService getInstance() {
		return informaService;
	}

	@Override
	public void onUpdate(long timestamp, LogPack logPack) {
		// TODO Auto-generated method stub
		if(cache.log == null) {
			cache.log = new List<LogPack>();
		}
		
	}

	@Override
	public void onUpdate(LogPack logPack) {
		// TODO Auto-generated method stub
		
	}

}
