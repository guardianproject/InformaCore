package org.witness.informacam.informa;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.Debug;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.EnvironmentalSucker;
import org.witness.informacam.informa.suckers.GeoFusedSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.models.j3m.ILocation;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.models.j3m.ISuckerCache;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;
import org.witness.informacam.utils.Constants.Suckers.Phone;
import org.witness.informacam.utils.MediaHasher;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class InformaService extends Service implements SuckerCacheListener {
	private final IBinder binder = new LocalBinder();
	private static InformaService informaService;

	ExecutorService ex;

	private long startTime = 0L;
	private long realStartTime = 0L;

	private int GPS_WAITING = 0;

	public SensorLogger<GeoSucker> _geo;
	public SensorLogger<PhoneSucker> _phone;
	public SensorLogger<AccelerometerSucker> _acc;
	public SensorLogger<EnvironmentalSucker> _env;

	private info.guardianproject.iocipher.File cacheFile, cacheRoot;
	private List<String> cacheFiles = new ArrayList<String>();
	private LoadingCache<Long, ILogPack> cache = null;
	
	private final static long CACHE_MAX = 500;
	private Timer cacheTimer;
	
	InformaCam informaCam;

	Handler h = new Handler();
	String associatedMedia = null;
	
	Intent stopIntent = new Intent().setAction(Actions.INFORMA_STOP);

	private InformaBroadcaster[] broadcasters = {
			new InformaBroadcaster(new IntentFilter(BluetoothDevice.ACTION_FOUND)),
			new InformaBroadcaster(new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
	};

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
		
		if (Debug.WAIT_FOR_DEBUGGER)
			android.os.Debug.waitForDebugger();
		
		informaCam =  (InformaCam)getApplication();

		if (informaCam.ioService == null || (!informaCam.ioService.isMounted()))
		{
			//this seems like an auto-restart; we should stop
			stopSelf();
			return;
		}
		
		for(BroadcastReceiver broadcaster : broadcasters) {
			this.registerReceiver(broadcaster, ((InformaBroadcaster) broadcaster).intentFilter);
		}
		
		
		cacheRoot = new info.guardianproject.iocipher.File(IManifest.CACHES);
		if(!cacheRoot.exists()) {
			cacheRoot.mkdir();
		}

		initCache();

		_geo = new GeoFusedSucker(this);
		_geo.setSuckerCacheListener(this);
		
		_phone = new PhoneSucker(this);
		_phone.setSuckerCacheListener(this);
		
		_acc = new AccelerometerSucker(this);
		_acc.setSuckerCacheListener(this);
		
		_env = new EnvironmentalSucker(this);
		_env.setSuckerCacheListener(this);
		
		informaService = InformaService.this;
		sendBroadcast(new Intent()
			.putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE)
			.setAction(Actions.ASSOCIATE_SERVICE)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, android.os.Process.myPid()));

		init();
	}
	
	public ILocation getCurrentLocation() {
		return new ILocation(((GeoSucker) _geo).updateLocation());
	}

	public long getCurrentTime() {
		return System.currentTimeMillis() + (realStartTime == 0 ? 0 : (startTime - realStartTime));
	}
	
	public long getTimeOffset() {
		return realStartTime == 0 ? 0 : (startTime - realStartTime);
	}

	public void associateMedia(IMedia media) {
		this.associatedMedia = media._id;
	}
	
	public void unassociateMedia() {
		this.associatedMedia = null;
	}
	
	private void init() {
		h.post(new Runnable() {
			@Override
			public void run() {
				
				if (_geo == null || _phone == null)
					return; //suckers are not init'd
				
				startTime = System.currentTimeMillis();
				long currentTime = 0;
				
				currentTime = ((GeoSucker) _geo).getTime();				
				
				if(currentTime != 0) {
					realStartTime = currentTime;					
				}
								
				double[] currentLocation = null;
				
				currentLocation = ((GeoSucker) _geo).updateLocation();
				
				if(currentTime == 0 || currentLocation == null) {
					GPS_WAITING++;

					if(GPS_WAITING < Suckers.GPS_WAIT_MAX) {
						h.postDelayed(this, 200);
						return;
					} else {
						Toast.makeText(InformaService.this, getString(R.string.gps_not_available_your), Toast.LENGTH_LONG).show();
					}
					
				}

				onUpdate(((GeoSucker) _geo).forceReturn());
				
				try {
					onUpdate(((PhoneSucker) _phone).forceReturn());
				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
				
				if (informaCam != null)
				{
					sendBroadcast(new Intent()
						.setAction(Actions.INFORMA_START)
						.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, informaCam.getProcess()));
				}
			}
		});

	}
	
	public void flushCache() {
		flushCache(null);
	}
	
	public void flushCache(IMedia m) {
		saveCache(true, m);
	}

	private void initCache() {
		try {
			cacheFile = new info.guardianproject.iocipher.File(cacheRoot, MediaHasher.hash(new String(startTime + "_" + System.currentTimeMillis()).getBytes(), "MD5"));
			cacheFiles.add(cacheFile.getAbsolutePath());
		} catch (NoSuchAlgorithmException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		cacheTimer = new Timer();
		cacheTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				if(cache != null) {
					
					if(cache.size() >= CACHE_MAX) {
						saveCache(true, null);
					}
				}
				
			}
			
		}, 0, 4000L);
		
		startTime = System.currentTimeMillis();
		cache = CacheBuilder.newBuilder()
				.build(new CacheLoader<Long, ILogPack>() {

					@Override
					public ILogPack load(Long timestamp) throws Exception {
						return cache.getUnchecked(timestamp);
					}

				});
		
	}
	
	private void saveCache() {
		saveCache(false, null);
	}

	private Thread mThread = null;
	
	private void saveCache(final boolean restartCache, final IMedia m) {	
		
		if (mThread == null || (!mThread.isAlive()))
		{

			if (cache == null) //service may have been stopped
				return;
			
			Log.d(LOG, "CACHE SIZE SO FAR: " + cache.size() + "\nSaving and restarting cache...");
			
			mThread = new Thread(new Runnable() {
				@Override
				public void run() {
					ISuckerCache suckerCache = new ISuckerCache();
					JSONArray cacheArray = new JSONArray();
	
					Iterator<Entry<Long, ILogPack>> cIt = cache.asMap().entrySet().iterator();
					while(cIt.hasNext()) {
						JSONObject cacheMap = new JSONObject();
						Entry<Long, ILogPack> c = cIt.next();
						try {
							cacheMap.put(String.valueOf(c.getKey()), c.getValue());
							cacheArray.put(cacheMap);
						} catch(JSONException e) {
							Logger.e(LOG, e);
						}
					}
									
					suckerCache.timeOffset = realStartTime;
					suckerCache.cache = cacheArray;
	
					informaCam.ioService.saveBlob(suckerCache.asJson().toString().getBytes(), cacheFile);
	
					if(associatedMedia != null) {
						IMedia media = informaCam.mediaManifest.getById(associatedMedia);
						if(media.associatedCaches == null) {
							media.associatedCaches = new ArrayList<String>();
						}
	
						if(!media.associatedCaches.contains(cacheFile.getAbsolutePath())) { 
							media.associatedCaches.add(cacheFile.getAbsolutePath());
						}
	
						Logger.d(LOG, "OK-- I am about to save the cache reference.  is this still correct?\n" + media.asJson().toString());
						media.save();
					}
					
					if(m != null) {
						associateMedia(m);
					}
					
					InformaService.this.onCacheSaved(restartCache);
				}
			});
			
			mThread.start();		
		}
		else
		{

			Log.d(LOG, "CACHE SAVE IN PROGRESS... WAITING IN LINE ...");
		}
		
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		saveCache();

		try {
			_phone.getSucker().stopUpdates();
			_acc.getSucker().stopUpdates();
			_geo.getSucker().stopUpdates();
			_env.getSucker().stopUpdates();
		} catch(NullPointerException e) {
			e.printStackTrace();
		}

		_geo = null;
		_phone = null;
		_acc = null;
		_env = null;

		for(BroadcastReceiver b : broadcasters) {
			
			try
			{
				unregisterReceiver(b);
			}
			catch (IllegalArgumentException iae)
			{
				//some broadcasters may not be registered; don't let this stop us from getting destroyed!
			}
		}

		sendBroadcast(stopIntent.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, informaCam.getProcess()));
		
		sendBroadcast(new Intent()
			.putExtra(Codes.Keys.SERVICE, Codes.Routes.INFORMA_SERVICE)
			.setAction(Actions.DISASSOCIATE_SERVICE)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, android.os.Process.myPid()));
	}

	public static InformaService getInstance() {
		return informaService;
	}

	public List<ILogPack> getAllEventsByType(final int type) throws InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<List<ILogPack>> query = ex.submit(new Callable<List<ILogPack>>() {

			@Override
			public List<ILogPack> call() throws Exception {
				Iterator<Entry<Long, ILogPack>> cIt = cache.asMap().entrySet().iterator();
				List<ILogPack> events = new ArrayList<ILogPack>();
				while(cIt.hasNext()) {
					Entry<Long, ILogPack> entry = cIt.next();
					if(entry.getValue().has(CaptureEvent.Keys.TYPE) && entry.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						events.add(entry.getValue());
				}

				return events;
			}
		});

		List<ILogPack> events = query.get();
		ex.shutdown();

		return events;
	}

	public List<Entry<Long, ILogPack>> getAllEventsByTypeWithTimestamp(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<List<Entry<Long, ILogPack>>> query = ex.submit(new Callable<List<Entry<Long, ILogPack>>>() {

			@Override
			public List<Entry<Long, ILogPack>> call() throws Exception {
				Iterator<Entry<Long, ILogPack>> cIt = cache.asMap().entrySet().iterator();
				List<Entry<Long, ILogPack>> events = new ArrayList<Entry<Long, ILogPack>>();
				while(cIt.hasNext()) {
					Entry<Long, ILogPack> entry = cIt.next();
					if(entry.getValue().has(CaptureEvent.Keys.TYPE) && entry.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						events.add(entry);
				}

				return events;
			}
		});

		List<Entry<Long, ILogPack>> events = query.get();
		ex.shutdown();

		return events;
	}

	public Entry<Long, ILogPack> getEventByTypeWithTimestamp(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<Entry<Long, ILogPack>> query = ex.submit(new Callable<Entry<Long, ILogPack>>() {

			@Override
			public Entry<Long, ILogPack> call() throws Exception {
				Iterator<Entry<Long, ILogPack>> cIt = cache.asMap().entrySet().iterator();
				Entry<Long, ILogPack> entry = null;
				while(cIt.hasNext() && entry == null) {
					Entry<Long, ILogPack> e = cIt.next();
					if(e.getValue().has(CaptureEvent.Keys.TYPE) && e.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						entry = e;
				}

				return entry;
			}
		});

		Entry<Long, ILogPack> entry = query.get();
		ex.shutdown();

		return entry;
	}

	public ILogPack getEventByType(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<ILogPack> query = ex.submit(new Callable<ILogPack>() {

			@Override
			public ILogPack call() throws Exception {
				Iterator<ILogPack> cIt = cache.asMap().values().iterator();
				ILogPack ILogPack = null;
				while(cIt.hasNext() && ILogPack == null) {
					ILogPack lp = cIt.next();

					if(lp.has(CaptureEvent.Keys.TYPE) && lp.getInt(CaptureEvent.Keys.TYPE) == type)
						ILogPack = lp;
				}

				return ILogPack;
			}

		});
		ILogPack ILogPack = query.get();
		ex.shutdown();

		return ILogPack;
	}

	@SuppressWarnings("unchecked")
	public boolean removeRegion(IRegion region) {
		try { 
			ILogPack ILogPack = cache.getIfPresent(region.timestamp);
			if(ILogPack.has(CaptureEvent.Keys.TYPE) && ILogPack.getInt(CaptureEvent.Keys.TYPE) == CaptureEvent.REGION_GENERATED) {
				ILogPack.remove(CaptureEvent.Keys.TYPE);
			}

			Iterator<String> repIt = region.asJson().keys();
			while(repIt.hasNext()) {
				ILogPack.remove(repIt.next());
			}

			return true;
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	public void addRegion(IRegion region) {
		ILogPack ILogPack = new ILogPack(CaptureEvent.Keys.TYPE, CaptureEvent.REGION_GENERATED, true);
		ILogPack regionLocationData = ((GeoSucker) _geo).forceReturn();
		try {
			ILogPack.put(CaptureEvent.Keys.REGION_LOCATION_DATA, regionLocationData);
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		Iterator<String> rIt = region.asJson().keys();
		while(rIt.hasNext()) {
			String key = rIt.next();
			try {
				ILogPack.put(key, region.asJson().get(key));
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		Log.d(LOG, "HEY NEW REGION: " + ILogPack.asJson().toString());
		region.timestamp = onUpdate(ILogPack);
	}

	@SuppressWarnings("unchecked")
	public void updateRegion(IRegion region) {
		try {
			ILogPack ILogPack = cache.getIfPresent(region.timestamp);
			Iterator<String> repIt = region.asJson().keys();
			while(repIt.hasNext()) {
				String key = repIt.next();
				ILogPack.put(key, region.asJson().get(key));
			}
		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch(NullPointerException e) {
			Log.e(LOG, "CONSIDERED HANDLED:\n" + e.toString());
			e.printStackTrace();
			
			addRegion(region);
		}

	}

	@SuppressWarnings({ "unchecked", "unused" })
	private ILogPack JSONObjectToILogPack(JSONObject json) throws JSONException {
		ILogPack ILogPack = new ILogPack();
		Iterator<String> jIt = json.keys();
		while(jIt.hasNext()) {
			String key = jIt.next();
			ILogPack.put(key, json.get(key));
		}
		return ILogPack;
	}

	@SuppressWarnings("unused")
	private void pushToSucker(SensorLogger<?> sucker, ILogPack ILogPack) throws JSONException {
		if(sucker.getClass().equals(PhoneSucker.class))
			_phone.sendToBuffer(ILogPack);
	}
	
	public String getCacheFile() {
		return cacheFile.getAbsolutePath();
	}
	
	public List<String> getCacheFiles() {
		return cacheFiles;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onUpdate(long timestamp, ILogPack ILogPack) {
		try {
			ILogPack lp = cache.getIfPresent(timestamp);
			if(lp != null) {
				Iterator<String> lIt = lp.keys();
				while(lIt.hasNext()) {
					String key = lIt.next();
					ILogPack.put(key, lp.get(key));	
				}
			}

			cache.put(timestamp, ILogPack);
		} catch(JSONException e) {}
	}

	@Override
	public long onUpdate(ILogPack ILogPack) {
		long timestamp = getCurrentTime();
		onUpdate(timestamp, ILogPack);
		
		return timestamp;
	}

	class InformaBroadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;

		public InformaBroadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
				try {
					BluetoothDevice bd = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					ILogPack logPack = new ILogPack(Phone.Keys.BLUETOOTH_DEVICE_ADDRESS, bd.getAddress());					
					logPack.put(Phone.Keys.BLUETOOTH_DEVICE_NAME, bd.getName());					
					onUpdate(logPack);

				} catch(JSONException e) {}

			} else if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
				try {
					ILogPack ILogPack = new ILogPack(Phone.Keys.VISIBLE_WIFI_NETWORKS, ((PhoneSucker) informaService._phone).getWifiNetworks());
					onUpdate(ILogPack);
				} catch(NullPointerException e) {
					Log.e(LOG, "CONSIDERED HANDLED:\n" + e.toString());
					e.printStackTrace();
				}

			}

		}

	}

	private void onCacheSaved(boolean restartCache) {
		cacheTimer.cancel();
		
		if(restartCache) {
			initCache();
		}
	}
}