package org.witness.informacam.informa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.MainActivity;
import org.witness.informacam.app.editors.image.ImageRegion;
import org.witness.informacam.app.editors.image.ImageRegion.ImageRegionListener;
import org.witness.informacam.crypto.SignatureUtility;
import org.witness.informacam.informa.Informa.Exif;
import org.witness.informacam.informa.Informa.InformaListener;
import org.witness.informacam.informa.SensorLogger.OnUpdateListener;
import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Crypto.Signatures;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.Status;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Phone;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class InformaService extends Service implements OnUpdateListener, InformaListener, ImageRegionListener {
	public static InformaService informaService;
	private final IBinder binder = new LocalBinder();
	
	NotificationManager nm;
	
	Intent toMainActivity;
	private String informaCurrentStatusString;
	private int informaCurrentStatus;
		
	SensorLogger<GeoSucker> _geo;
	SensorLogger<PhoneSucker> _phone;
	SensorLogger<AccelerometerSucker> _acc;
	
	List<BroadcastReceiver> br = new ArrayList<BroadcastReceiver>();
	
	private LoadingCache<Long, LogPack> suckerCache;
	ExecutorService ex;
	
	Informa informa;
	
	long[] encryptList;
	
	String LOG = Constants.Informa.LOG;
	
	public class LocalBinder extends Binder {
		public InformaService getService() {
			return InformaService.this;
		}
	}
	
	public void setCurrentStatus(int status) {
		informaCurrentStatus = status;
		informaCurrentStatusString = getResources().getStringArray(R.array.informa_statuses)[informaCurrentStatus];
		showNotification();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	public static InformaService getInstance() {
		return informaService;
	}
	
	public List<Entry<Long, LogPack>> getAllEventsByTypeWithTimestamp(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<List<Entry<Long, LogPack>>> query = ex.submit(new Callable<List<Entry<Long, LogPack>>>() {
			
			@Override
			public List<Entry<Long, LogPack>> call() throws Exception {
				Iterator<Entry<Long, LogPack>> cIt = suckerCache.asMap().entrySet().iterator();
				List<Entry<Long, LogPack>> entries = new ArrayList<Entry<Long, LogPack>>();
				while(cIt.hasNext()) {
					Entry<Long, LogPack> entry = cIt.next();
					if(entry.getValue().has(CaptureEvent.Keys.TYPE) && entry.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						entries.add(entry);
				}
				
				return entries;
			}
		});
		
		List<Entry<Long, LogPack>> entries = query.get();
		ex.shutdown();
		
		return entries;
	}
	
	public Entry<Long, LogPack> getEventByTypeWithTimestamp(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<Entry<Long, LogPack>> query = ex.submit(new Callable<Entry<Long, LogPack>>() {
			
			@Override
			public Entry<Long, LogPack> call() throws Exception {
				Iterator<Entry<Long, LogPack>> cIt = suckerCache.asMap().entrySet().iterator();
				Entry<Long, LogPack> entry = null;
				while(cIt.hasNext() && entry == null) {
					Entry<Long, LogPack> e = cIt.next();
					if(e.getValue().has(CaptureEvent.Keys.TYPE) && e.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						entry = e;
				}
				
				return entry;
			}
		});
		
		Entry<Long, LogPack> entry = query.get();
		ex.shutdown();
		
		return entry;
	}
	
	public LogPack getEventByType(final int type) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<LogPack> query = ex.submit(new Callable<LogPack>() {

			@Override
			public LogPack call() throws Exception {
				Iterator<LogPack> cIt = suckerCache.asMap().values().iterator();
				LogPack logPack = null;
				while(cIt.hasNext() && logPack == null) {
					LogPack lp = cIt.next();
					
					Log.d(Storage.LOG, "querying logpacks for type " + type + "\n" + lp);
					if(lp.has(CaptureEvent.Keys.TYPE) && lp.getInt(CaptureEvent.Keys.TYPE) == type)
						logPack = lp;
				}
				
				return logPack;
			}
			
		});
		LogPack logPack = query.get();
		ex.shutdown();
		
		return logPack;
	}
	
	public int getStatus() {
		return informaCurrentStatus;
	}
	
	@Override
	public void onCreate() {
		Log.d(Constants.Informa.LOG, "InformaService running");
		
		toMainActivity = new Intent(this, MainActivity.class);
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		br.add(new Broadcaster(new IntentFilter(BluetoothDevice.ACTION_FOUND)));
		
		for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b).intentFilter);
		
		informaService = this;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(LOG, "InformaService stopped");
	}
			
	@SuppressWarnings({"unchecked"})
	public void init() {
		suckerCache = CacheBuilder.newBuilder()
				.build(new CacheLoader<Long, LogPack>() {
					@Override
					public LogPack load(Long timestamp) throws Exception {
						return suckerCache.get(timestamp);
					}
				});
		
		_geo = new GeoSucker(InformaService.this);
		_phone = new PhoneSucker(InformaService.this);
		_acc = new AccelerometerSucker(InformaService.this);
		this.setCurrentStatus(Status.RUNNING);
	}
	
	public void suspend() {
		this.setCurrentStatus(Status.STOPPED);
		_geo.getSucker().stopUpdates();
		_phone.getSucker().stopUpdates();
		_acc.getSucker().stopUpdates();
		
		_geo = null;
		_phone = null;
		_acc = null;
	}
	
	@SuppressWarnings("unused")
	private void doShutdown() {
		suspend();
		
		for(BroadcastReceiver b : br)
			unregisterReceiver(b);
		
		stopSelf();
	}
	
	@SuppressWarnings("deprecation")
	public void showNotification() {
		Notification n = new Notification(
				R.drawable.ic_ssc,
				getString(R.string.app_name),
				System.currentTimeMillis());
		
		PendingIntent pi = PendingIntent.getActivity(
				this,
				Constants.Informa.FROM_NOTIFICATION_BAR, 
				toMainActivity,
				PendingIntent.FLAG_UPDATE_CURRENT);
		
		n.setLatestEventInfo(this, getString(R.string.app_name), informaCurrentStatusString, pi);
		nm.notify(R.string.app_name_lc, n);
	}
	
	@SuppressWarnings("unused")
	private void pushToSucker(SensorLogger<?> sucker, LogPack logPack) throws JSONException {
		if(sucker.getClass().equals(PhoneSucker.class))
			_phone.sendToBuffer(logPack);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onUpdate(long timestamp, final LogPack logPack) {
		try {
			//Log.d(Suckers.LOG, timestamp + " :\n" + logPack.toString());
			ex = Executors.newFixedThreadPool(100);
			Future<String> sig = ex.submit(new Callable<String>() {

				@Override
				public String call() throws Exception {
					return SignatureUtility.getInstance().signData(logPack.toString().getBytes());
				}
				
			});
			
			LogPack lp = suckerCache.getIfPresent(timestamp);
			if(lp != null) {
				Iterator<String> lIt = lp.keys();
				while(lIt.hasNext()) {
					String key = lIt.next();
					logPack.put(key, lp.get(key));
				}
			}
			
			logPack.put(Signatures.Keys.SIGNATURE, sig.get());
			suckerCache.put(timestamp, logPack);
			ex.shutdown();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onInformaInit() {
		informa = new Informa(this);
		try {
			informa.setDeviceCredentials(_phone.getSucker().forceReturn());			
		} catch(JSONException e) {}
	}
	
	public void packageInforma() throws JSONException, InterruptedException, ExecutionException, IllegalArgumentException, IllegalAccessException {
		// set metadata
		informa.setInitialData(getEventByTypeWithTimestamp(CaptureEvent.METADATA_CAPTURED));
		informa.addToPlayback(getAllEventsByTypeWithTimestamp(CaptureEvent.SENSOR_PLAYBACK));
		informa.addToAnnotations(getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED));		
	}
	
	public void setEncryptionList(long[] encryptList) {
		this.encryptList = encryptList;
	}
	
	@SuppressWarnings("unchecked")
	private void changeRegion(JSONObject rep) {
		try {
			long timestamp = 0L;
			
			try {
				timestamp = (Long) rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP);
			} catch(ClassCastException e) {
				timestamp = Long.parseLong((String) rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP));
			}
			
			LogPack logPack = suckerCache.get(timestamp);
			logPack.remove(Signatures.Keys.SIGNATURE);
			Iterator<String> repIt = rep.keys();
			while(repIt.hasNext()) {
				String key = repIt.next();
				logPack.put(key, rep.get(key));
			}
			
			onUpdate(timestamp, logPack);
		} catch(JSONException e) {}
		catch (ExecutionException e) {
			
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void removeRegion(JSONObject rep) {
		try {
			long timestamp = 0L;
			
			try {
				timestamp = (Long) rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP);
			} catch(ClassCastException e) {
				timestamp = Long.parseLong((String) rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP));
			}
			
			LogPack logPack = suckerCache.get(timestamp);
			logPack.remove(Signatures.Keys.SIGNATURE);
			Iterator<String> repIt = rep.keys();
			while(repIt.hasNext())
				logPack.remove(repIt.next());
			
		} catch (ExecutionException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void addRegion(JSONObject rep) {
		try {
			long timestamp = (Long) rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP);
			
			LogPack logPack = new LogPack();
			Iterator<String> repIt = rep.keys();
			while(repIt.hasNext()) {
				String key = repIt.next();
				logPack.put(key, rep.get(key));
			}
			
			onUpdate(timestamp, logPack);
		} catch(JSONException e) {}
	}

	@Override
	public void onImageRegionCreated(ImageRegion ir) {
		try {
			addRegion(ir.getRepresentation());
		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	@Override
	public void onImageRegionChanged(ImageRegion ir) {
		try {
			changeRegion(ir.getRepresentation());
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	@Override
	public void onImageRegionRemoved(ImageRegion ir) {
		try {
			removeRegion(ir.getRepresentation());
		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();	
		}
	}
	
	public class ImageConstructor {
		long[] encryptList;
		
		public native int constructImage(
				String originalImageFilename, 
				String informaImageFilename, 
				String metadataObjectString, 
				int metadataLength);
		
		public native byte[] redactRegion(
				String originalImageFilename,
				String informaImageFilename,
				int left,
				int right,
				int top,
				int bottom,
				String redactionCommand);
		
		public ImageConstructor(long[] encryptList) {
			this.encryptList = encryptList;
			
			// get all image regions and run through image constructor
			
			// when done, for each in the encrypt list, insert metadata
			
			// add to upload queue if possible
			
			/* TODO HERE!
			(CaptureEvent.Keys.TYPE, CaptureEvent.MEDIA_SAVED);
			*/
		}
	}
	
	public class VideoConstructor {
		public VideoConstructor() {
			
		}
	}
	
	private class Broadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;
		
		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}
		
		@Override
		public void onReceive(Context c, Intent i) {
			if(BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
				try {
					BluetoothDevice bd = (BluetoothDevice) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					LogPack logPack = new LogPack(Phone.Keys.BLUETOOTH_DEVICE_ADDRESS, bd.getAddress());
					logPack.put(Phone.Keys.BLUETOOTH_DEVICE_NAME, bd.getName());
					suckerCache.put(System.currentTimeMillis(), logPack);
				} catch(JSONException e) {}
			}
			
		}
	}
}