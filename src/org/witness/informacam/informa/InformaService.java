package org.witness.informacam.informa;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.MainActivity;
import org.witness.informacam.app.editors.image.ImageConstructor;
import org.witness.informacam.app.editors.image.ImageRegion;
import org.witness.informacam.app.editors.image.ImageRegion.ImageRegionListener;
import org.witness.informacam.app.editors.video.RegionTrail;
import org.witness.informacam.app.editors.video.RegionTrail.VideoRegionListener;
import org.witness.informacam.app.editors.video.VideoConstructor;
import org.witness.informacam.informa.Informa;
import org.witness.informacam.informa.Informa.InformaListener;
import org.witness.informacam.informa.SensorLogger.OnUpdateListener;
import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.storage.IOCipherService.IOCipherServiceListener;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.Status;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Phone;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class InformaService extends Service implements OnUpdateListener, InformaListener, ImageRegionListener, VideoRegionListener, IOCipherServiceListener {
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
	
	private LoadingCache<Long, LogPack> suckerCache, annotationCache;
	ExecutorService ex;
	
	public Informa informa;
	Activity editor;
	
	public Uri originalUri;
	long[] encryptList;
	
	String LOG = Constants.Informa.LOG;
	
	private boolean isBlocking = true; 
	
	public interface InformaServiceListener {
		public void onInformaPackageGenerated();
	}
	
	public class LocalBinder extends Binder {
		public InformaService getService() {
			return InformaService.this;
		}
	}
	
	public void versionsCreated() {
		// TODO: cleanup, add to upload queue, etc
		java.io.File imgTemp = new java.io.File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.IMAGE_TMP);
		if(imgTemp.exists())
			imgTemp.delete();
		
		java.io.File vidTemp = new java.io.File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.VIDEO_TMP);
		if(vidTemp.exists()) {
			vidTemp.delete();
		}
		
		java.io.File vidMetadata = new java.io.File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.TMP_VIDEO_DATA_FILE_NAME);
		if(vidMetadata.exists())
			vidMetadata.delete();
		
		Intent intent = new Intent(this, UploaderService.class);
		startService(intent);
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
	
	public List<LogPack> getAllEventsByType(final int type, final LoadingCache<Long, LogPack> cache) throws InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<List<LogPack>> query = ex.submit(new Callable<List<LogPack>>() {
			
			@Override
			public List<LogPack> call() throws Exception {
				Iterator<Entry<Long, LogPack>> cIt = cache.asMap().entrySet().iterator();
				List<LogPack> events = new ArrayList<LogPack>();
				while(cIt.hasNext()) {
					Entry<Long, LogPack> entry = cIt.next();
					if(entry.getValue().has(CaptureEvent.Keys.TYPE) && entry.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						events.add(entry.getValue());
				}
				
				return events;
			}
		});
		
		List<LogPack> events = query.get();
		ex.shutdown();
		
		return events;
	}
	
	public List<Entry<Long, LogPack>> getAllEventsByTypeWithTimestamp(final int type, final LoadingCache<Long, LogPack> cache) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<List<Entry<Long, LogPack>>> query = ex.submit(new Callable<List<Entry<Long, LogPack>>>() {
			
			@Override
			public List<Entry<Long, LogPack>> call() throws Exception {
				Iterator<Entry<Long, LogPack>> cIt = cache.asMap().entrySet().iterator();
				List<Entry<Long, LogPack>> events = new ArrayList<Entry<Long, LogPack>>();
				while(cIt.hasNext()) {
					Entry<Long, LogPack> entry = cIt.next();
					if(entry.getValue().has(CaptureEvent.Keys.TYPE) && entry.getValue().getInt(CaptureEvent.Keys.TYPE) == type)
						events.add(entry);
				}
				
				return events;
			}
		});
		
		List<Entry<Long, LogPack>> events = query.get();
		ex.shutdown();
		
		return events;
	}
	
	public Entry<Long, LogPack> getEventByTypeWithTimestamp(final int type, final LoadingCache<Long, LogPack> cache) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<Entry<Long, LogPack>> query = ex.submit(new Callable<Entry<Long, LogPack>>() {
			
			@Override
			public Entry<Long, LogPack> call() throws Exception {
				Iterator<Entry<Long, LogPack>> cIt = cache.asMap().entrySet().iterator();
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
	
	public LogPack getEventByType(final int type, final LoadingCache<Long, LogPack> cache) throws JSONException, InterruptedException, ExecutionException {
		ex = Executors.newFixedThreadPool(100);
		Future<LogPack> query = ex.submit(new Callable<LogPack>() {

			@Override
			public LogPack call() throws Exception {
				Iterator<LogPack> cIt = cache.asMap().values().iterator();
				LogPack logPack = null;
				while(cIt.hasNext() && logPack == null) {
					LogPack lp = cIt.next();
					
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
			
	@SuppressWarnings("unchecked")
	public void init() {
		initCaches();
		
		_geo = new GeoSucker(InformaService.this);
		_phone = new PhoneSucker(InformaService.this);
		_acc = new AccelerometerSucker(InformaService.this);
		this.setCurrentStatus(Status.RUNNING);
	}
	
	private void initCaches() {
		if(suckerCache != null)
			suckerCache = null;
		
		suckerCache = CacheBuilder.newBuilder()
				//.maximumSize(640) //64 bytes per entry, 200 entries = 128000
				//.removalListener(new CacheAllocator())
				.build(new CacheLoader<Long, LogPack>() {
					@Override
					public LogPack load(Long timestamp) throws Exception {
						return suckerCache.get(timestamp);
					}
				});
		
		if(annotationCache != null)
			annotationCache = null;
		
		annotationCache = CacheBuilder.newBuilder()
				//.maximumSize(640) //64 bytes per entry, 200 entries = 128000
				//.removalListener(new CacheAllocator())
				.build(new CacheLoader<Long, LogPack>() {
					@Override
					public LogPack load(Long timestamp) throws Exception {
						return annotationCache.get(timestamp);
					}
				});
	}
	
	public void suspend() {
		this.setCurrentStatus(Status.STOPPED);
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
			LogPack lp = null;
			
			
			switch(logPack.getInt(CaptureEvent.Keys.TYPE)) {
			case CaptureEvent.SENSOR_PLAYBACK:
				lp = suckerCache.getIfPresent(timestamp);
				if(lp != null) {
					//Log.d(Suckers.LOG, "already have " + timestamp + " :\n" + lp.toString());
					Iterator<String> lIt = lp.keys();
					while(lIt.hasNext()) {
						String key = lIt.next();
						logPack.put(key, lp.get(key));
							
					}
				}
				suckerCache.put(timestamp, logPack);
				break;
			default:
				Log.d(Suckers.LOG, "LOGGING " + timestamp + " :\n" + logPack.toString());
				lp = annotationCache.getIfPresent(timestamp);
				if(lp != null) {
					Iterator<String> lIt = lp.keys();
					while(lIt.hasNext()) {
						String key = lIt.next();
						logPack.put(key, lp.get(key));
							
					}
				}
				annotationCache.put(timestamp, logPack);
				break;
			}
			
		} catch (JSONException e) {}
	}

	@Override
	public void onInformaInit(Activity editor, Uri originalUri) {
		informa = new Informa(this);
		try {
			informa.setDeviceCredentials(_phone.getSucker().forceReturn());
			informa.setFileInformation(originalUri.toString());
		} catch(JSONException e) {}
		
		this.editor = editor;
		this.originalUri = originalUri;
	}
	
	public void packageInforma() {
		Thread packageInforma = new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							if(informa.setInitialData(getEventByTypeWithTimestamp(CaptureEvent.METADATA_CAPTURED, annotationCache)))
								if(informa.addToPlayback(getAllEventsByTypeWithTimestamp(CaptureEvent.SENSOR_PLAYBACK, suckerCache))) {
									((InformaServiceListener) editor).onInformaPackageGenerated();
									suspend();

									if(editor.getLocalClassName().equals(App.ImageEditor.TAG)) {
										Log.d(Storage.LOG, "...waiting for image to resave...");
										long time = System.currentTimeMillis();
										do {
											// do nothing!
										} while(isBlocking);
										Log.d(Storage.LOG, "saving took " + Math.abs(time - System.currentTimeMillis()) + " ms");
										Log.d(Storage.LOG, "no longer blocking!");
										ImageConstructor imageConstructor = new ImageConstructor(InformaService.this.getApplicationContext(), annotationCache, encryptList);
									} else if(editor.getLocalClassName().equals(App.VideoEditor.TAG)) {
										isBlocking = false;
										VideoConstructor.getVideoConstructor().buildInformaVideo(InformaService.this.getApplicationContext(), annotationCache, encryptList);
									}
										
								}
						} catch(JSONException e){}
						catch (InterruptedException e) {
							e.printStackTrace();
						} catch (ExecutionException e) {
							e.printStackTrace();
						}
					}
				});
		packageInforma.start();
	}
	
	public LogPack getMetadata() throws JSONException, InterruptedException, ExecutionException {
		return getEventByType(CaptureEvent.METADATA_CAPTURED, annotationCache);
	}
	
	public void setEncryptionList(long[] encryptList) {
		this.encryptList = encryptList;
	}
	
	@SuppressWarnings("unchecked")
	private void changeRegion(JSONObject rep) {
		try {
			long timestamp = 0L;
			
			try {
				timestamp = (Long) rep.get(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP);
			} catch(ClassCastException e) {
				timestamp = Long.parseLong((String) rep.get(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP));
			}
			
			rep.remove(Constants.Informa.Keys.Data.ImageRegion.TIMESTAMP);
			
			LogPack logPack = annotationCache.get(timestamp);
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
			
			LogPack logPack = annotationCache.get(timestamp);
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
			
			LogPack logPack = new LogPack(CaptureEvent.Keys.TYPE, CaptureEvent.REGION_GENERATED);
			Iterator<String> repIt = rep.keys();
			while(repIt.hasNext()) {
				String key = repIt.next();
				logPack.put(key, rep.get(key));
			}
			
			onUpdate(timestamp, logPack);
		} catch(JSONException e) {}
	}

	@Override
	public void onImageRegionCreated(final ImageRegion ir) {
		new Thread(
			new Runnable() {
				@Override
				public void run() {
					try {
						addRegion(ir.getRepresentation());
					} catch(JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
		}).start();
		
	}

	@Override
	public void onImageRegionChanged(final ImageRegion ir) {
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							changeRegion(ir.getRepresentation());
						} catch (JSONException e) {
							Log.e(LOG, e.toString());
							e.printStackTrace();
						}
					}
			}).start();
	}

	@Override
	public void onImageRegionRemoved(final ImageRegion ir) {
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							removeRegion(ir.getRepresentation());
						} catch(JSONException e) {
							Log.e(LOG, e.toString());
							e.printStackTrace();	
						}
					}
			}).start();
		
	}
	
	@Override
	public void onVideoRegionCreated(final RegionTrail rt) {
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							addRegion(rt.getRepresentation());
						} catch(JSONException e) {
							Log.e(LOG, e.toString());
						}
					}
				
			}).start();
		
	}

	@Override
	public void onVideoRegionChanged(final RegionTrail rt) {
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							changeRegion(rt.getRepresentation());
						} catch(JSONException e) {
							Log.e(LOG, e.toString());
						}
					}
				
			}).start();
		
	}

	@Override
	public void onVideoRegionDeleted(final RegionTrail rt) {
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							removeRegion(rt.getRepresentation());
						} catch(JSONException e) {
							Log.e(LOG, e.toString());
						}
					}
				
			}).start();
		
	}
	
	@Override
	public void onBitmapResaved() {
		isBlocking = false;
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