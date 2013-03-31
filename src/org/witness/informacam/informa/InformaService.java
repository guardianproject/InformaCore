package org.witness.informacam.informa;

import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.models.ISuckerCache;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.LogPack;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class InformaService extends Service implements SuckerCacheListener {
	private final IBinder binder = new LocalBinder();
	private static InformaService informaService;

	private long startTime = 0L;
	private long realStartTime = 0L;
	
	private int GPS_WAITING = 0;

	public SensorLogger<GeoSucker> _geo;
	public SensorLogger<PhoneSucker> _phone;
	public SensorLogger<AccelerometerSucker> _acc;
	
	private LoadingCache<Long, LogPack> cache;
	InformaCam informaCam;
	
	Handler h = new Handler();

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

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		informaCam = InformaCam.getInstance();
		
		initCache();

		_geo = new GeoSucker();
		_phone = new PhoneSucker();
		_acc = new AccelerometerSucker();

		informaService = InformaService.this;
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE).setAction(Actions.ASSOCIATE_SERVICE));
		
		init();
	}
	
	public long getCurrentTime() {
		try {
			return ((GeoSucker) _geo).getTime();
		} catch(NullPointerException e) {
			return 0;
		}
	}
	
	private void init() {
		h.post(new Runnable() {
			@Override
			public void run() {
				long currentTime = getCurrentTime();
				Log.d(LOG, "time: " + currentTime);
				if(currentTime <= 0) {
					GPS_WAITING++;
				
					if(GPS_WAITING < Suckers.GPS_WAIT_MAX) {
						h.postDelayed(this, 200);
					} else {
						Toast.makeText(InformaService.this, "NO GPS!", Toast.LENGTH_LONG).show();
						stopSelf();
					}
					return;
				}
				
				realStartTime = currentTime;
				onUpdate(((GeoSucker) _geo).forceReturn());
				try {
					onUpdate(((PhoneSucker) _phone).forceReturn());
				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
				sendBroadcast(new Intent().setAction(Actions.INFORMACAM_START));
			}
		});
		
	}
	
	private void initCache() {
		startTime = System.currentTimeMillis();
		cache = CacheBuilder.newBuilder()
				.build(new CacheLoader<Long, LogPack>() {

					@Override
					public LogPack load(Long timestamp) throws Exception {
						return cache.getUnchecked(timestamp);
					}
					
				});
	}
	
	private void saveCache() {
		info.guardianproject.iocipher.File cacheRoot = new info.guardianproject.iocipher.File(IManifest.CACHES);
		if(!cacheRoot.exists()) {
			cacheRoot.mkdir();
		}
		
		info.guardianproject.iocipher.File cacheFile = new info.guardianproject.iocipher.File(cacheRoot, startTime + "_" + System.currentTimeMillis());
		
		ISuckerCache suckerCache = new ISuckerCache();
		JSONArray cacheArray = new JSONArray();
		Iterator<Entry<Long, LogPack>> cIt = cache.asMap().entrySet().iterator();
		while(cIt.hasNext()) {
			JSONObject cacheMap = new JSONObject();
			Entry<Long, LogPack> c = cIt.next();
			try {
				cacheMap.put(String.valueOf(c.getKey()), c.getValue());
				cacheArray.put(cacheMap);
			} catch(JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		suckerCache.timeOffset = realStartTime;
		suckerCache.cache = cacheArray;
		
		Log.d(LOG, suckerCache.asJson().toString());
		informaCam.ioService.saveBlob(suckerCache.asJson().toString().getBytes(), cacheFile);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		saveCache();

		try {
			_geo.getSucker().stopUpdates();
			_phone.getSucker().stopUpdates();
			_acc.getSucker().stopUpdates();
		} catch(NullPointerException e) {
			e.printStackTrace();
		}

		_geo = null;
		_phone = null;
		_acc = null;
		
		sendBroadcast(new Intent().setAction(Actions.INFORMACAM_STOP));
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE).setAction(Actions.DISASSOCIATE_SERVICE));
	}

	public static InformaService getInstance() {
		return informaService;
	}

	@SuppressWarnings({ "unchecked", "unused" })
	private LogPack JSONObjectToLogPack(JSONObject json) throws JSONException {
		LogPack logPack = new LogPack();
		Iterator<String> jIt = json.keys();
		while(jIt.hasNext()) {
			String key = jIt.next();
			logPack.put(key, json.get(key));
		}
		return logPack;
	}
	
	@SuppressWarnings("unused")
	private void pushToSucker(SensorLogger<?> sucker, LogPack logPack) throws JSONException {
		if(sucker.getClass().equals(PhoneSucker.class))
			_phone.sendToBuffer(logPack);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onUpdate(long timestamp, LogPack logPack) {
		try {
			LogPack lp = cache.getIfPresent(timestamp);
			if(lp != null) {
				Iterator<String> lIt = lp.keys();
				while(lIt.hasNext()) {
					String key = lIt.next();
					logPack.put(key, lp.get(key));	
				}
			}
			
			cache.put(timestamp, logPack);
		} catch(JSONException e) {}
	}

	@Override
	public void onUpdate(LogPack logPack) {
		onUpdate(((GeoSucker) _geo).getTime(), logPack);
	}
}