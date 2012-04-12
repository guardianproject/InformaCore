package org.witness.informa.utils;

import info.guardianproject.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.Informa;
import org.witness.informa.Informa.Image;
import org.witness.informa.utils.InformaConstants.CaptureEvents;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.CaptureEvent;
import org.witness.informa.utils.InformaConstants.Keys.Suckers;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.OriginalImageHandling;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.suckers.*;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

@SuppressWarnings("unused")
public class SensorSucker extends Service {
	SensorLogger<GeoSucker> _geo;
	SensorLogger<PhoneSucker> _phone;
	SensorLogger<AccelerometerSucker> _acc;

	JSONArray capturedEvents, mediaRegions;
	JSONObject mediaData;
	Handler informaCallback;
	
	List<BroadcastReceiver> br = new ArrayList<BroadcastReceiver>();

	public class LocalBinder extends Binder {
		public SensorSucker getService() {
			return SensorSucker.this;
		}
	}
	
	private final IBinder binder = new LocalBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		
		startUpService();
	}
	
	public void onDestroy() {
		super.onDestroy();
		Log.d(InformaConstants.SUCKER_TAG, "SERVICE DEFINITELY DISCONNECTED");
		
		for(BroadcastReceiver b : br)
			unregisterReceiver(b);
	}
	
	public void stopSucking() {
		_geo.getSucker().stopUpdates();
		_phone.getSucker().stopUpdates();
		_acc.getSucker().stopUpdates();
		stopSelf();
	}
	
	@SuppressWarnings("unchecked")
	public void startUpService() {
		Log.d(InformaConstants.TAG, "Informa v1.1 starting");
		
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.STOP_SERVICE)));
		br.add(new Broadcaster(new IntentFilter(BluetoothDevice.ACTION_FOUND)));
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.SET_CURRENT)));
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.SEAL_LOG)));
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.LOCK_LOGS)));
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.UNLOCK_LOGS)));
		br.add(new Broadcaster(new IntentFilter(InformaConstants.Keys.Service.INFLATE_VIDEO_TRACK)));
		
		for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b)._filter);
		
		_geo = new GeoSucker(getApplicationContext());
		_phone = new PhoneSucker(getApplicationContext());
		_acc = new AccelerometerSucker(getApplicationContext());
		
		capturedEvents = mediaRegions = new JSONArray();
		mediaData = new JSONObject();
	}
	
	private void inflateFromLogs(long startFrom, long duration) throws JSONException {
		// 1. get the parts of from the log that concern us here
		ArrayList<JSONObject> logs = new ArrayList<JSONObject>();
		
		logs.addAll(truncateLog(_acc.getLog(), startFrom, (startFrom + duration)));
		logs.addAll(truncateLog(_geo.getLog(), startFrom, (startFrom + duration)));
		logs.addAll(truncateLog(_phone.getLog(), startFrom, (startFrom + duration)));
		
				
		Map<Long, JSONObject> videoLog = new ConcurrentHashMap<Long, JSONObject>();
		for(JSONObject entry : logs) {
			String cTag = null;
			if(entry.has(Suckers.Accelerometer.AZIMUTH) || entry.has(Suckers.Accelerometer.X) || entry.has(Suckers.Accelerometer.LIGHT_METER_VALUE))
				cTag = Suckers.ACCELEROMETER;
			else if(entry.has(Suckers.Geo.GPS_COORDS))
				cTag = Suckers.GEO;
			else if(entry.has(Suckers.Phone.CELL_ID))
				cTag = Suckers.PHONE;
			
			try {
				long timestamp = (Long) entry.remove(CaptureEvent.TIMESTAMP);
			
				if(videoLog.containsKey(timestamp)) {
					videoLog.get(timestamp).accumulate(cTag, entry);
				} else {
					JSONObject captureEvent = new JSONObject();
					captureEvent.put(cTag, entry);
					videoLog.put(timestamp, captureEvent);
				}
			} catch(NullPointerException e) {}
		}
		Log.d(InformaConstants.TAG, videoLog.toString());
		
		Iterator<Entry<Long, JSONObject>> i = videoLog.entrySet().iterator();
		while(i.hasNext()) {
			Entry<Long, JSONObject> event = i.next();
			JSONObject captureEventData = new JSONObject();
			captureEventData.put(CaptureEvent.TYPE, CaptureEvents.DURATIONAL_LOG);
			captureEventData.put(CaptureEvent.MATCH_TIMESTAMP, event.getKey());
			captureEventData.put(CaptureEvent.VIDEO_TRACK, event.getValue());
			capturedEvents.put(captureEventData);
		}
		
		Log.d(InformaConstants.TAG, "HUGE DUMP FOR THE VIDEO:\n" + capturedEvents.toString());
	}
	
	private ArrayList<JSONObject> truncateLog(JSONArray log, long start, long end) throws JSONException {
		ArrayList<JSONObject> newLog = new ArrayList<JSONObject>();
		Log.d(InformaConstants.TAG, "this timestamp: " + start + " to " + end);
		for(int i = 0; i < log.length(); i++) {
			JSONObject entry = log.getJSONObject(i);
			if(entry.getLong(CaptureEvent.TIMESTAMP) >= start && entry.getLong(CaptureEvent.TIMESTAMP) <= end) {
				newLog.add(entry);
			}
		}
		return newLog;
	}
	
	private void lockLogs() {
		Log.d(InformaConstants.TAG, "logs are now locked!");
		_phone.lockLog();
		_geo.lockLog();
		_acc.lockLog();
	}
	
	private void unlockLogs() {
		Log.d(InformaConstants.TAG, "logs are now unlocked!");
		_phone.unlockLog();
		_geo.unlockLog();
		_acc.unlockLog();
	}
	
	private void handleBluetooth(BluetoothDevice device) throws JSONException {
		JSONObject captureEventData = new JSONObject();
		
		captureEventData.put(CaptureEvent.TYPE, CaptureEvents.BLUETOOTH_DEVICE_SEEN);
		captureEventData.put(CaptureEvent.MATCH_TIMESTAMP, System.currentTimeMillis());
		captureEventData.put(Suckers.Phone.BLUETOOTH_DEVICE_NAME, device.getName());
		captureEventData.put(Suckers.Phone.BLUETOOTH_DEVICE_ADDRESS, device.getAddress());
		
		capturedEvents.put(captureEventData);
	}
	
	private void handleExif(String exif) throws JSONException {
		JSONObject captureEventData = new JSONObject();
		
		captureEventData.put(CaptureEvent.TYPE, InformaConstants.CaptureEvents.EXIF_REPORTED);
		captureEventData.put(Keys.Image.EXIF, (JSONObject) new JSONTokener(exif).nextValue());
		
		capturedEvents.put(captureEventData);
	}
	
	private void pushToSucker(SensorLogger<?> sucker, JSONObject payload) throws JSONException {
		if(sucker.getClass().equals(PhoneSucker.class))
			_phone.sendToBuffer(payload);
	}
	
	private void captureEventData(long timestampToMatch, int captureEvent) throws Exception {
		JSONObject captureEventData = new JSONObject();
		
		captureEventData.put(InformaConstants.Keys.CaptureEvent.TYPE, captureEvent);
		captureEventData.put(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, timestampToMatch);
		captureEventData.put(InformaConstants.Keys.Suckers.GEO, _geo.returnCurrent());
		captureEventData.put(InformaConstants.Keys.Suckers.PHONE, _phone.returnCurrent());
		captureEventData.put(InformaConstants.Keys.Suckers.ACCELEROMETER, _acc.returnCurrent());
		
		capturedEvents.put(captureEventData);
	}
	
	private void sealLog(String regionData, String localMediaPath, long[] encryptTo, int mediaType) throws Exception {
		mediaData.put(InformaConstants.Keys.Image.LOCAL_MEDIA_PATH, localMediaPath);
		final long[] intendedDestinations = encryptTo;
		mediaData.put(InformaConstants.Keys.Media.MEDIA_TYPE, mediaType);
		
		informaCallback = new Handler();
		Runnable r = null;
		
		mediaRegions = (JSONArray) new JSONTokener(regionData).nextValue();
		
		if(mediaType == MediaTypes.PHOTO) {
			
		
			r = new Runnable() {
				Informa informa; 
			
				@Override
				public void run() {
					try {
						informa = new Informa(getApplicationContext(), mediaData, mediaRegions, capturedEvents, intendedDestinations);
						Image[] img = informa.getImages();
						final ArrayList<Map<File, String>> imgMap = new ArrayList<Map<File, String>>();
						
						ImageConstructor ic = new ImageConstructor(getApplicationContext(), img[0].getMetadataPackage(), img[0].getName());
						for(Image i : img) {
							ic.createVersionForTrustedDestination(i.getAbsolutePath(),i.getIntendedDestination());
							Map<File, String> iMap = new HashMap<File, String>();
							iMap.put(i.getAbsoluteFile(), i.getIntendedDestination());
							imgMap.add(iMap);
						}
								
						ic.doCleanup();
						
						informaCallback.post(new Runnable() {
							@Override
							public void run() {
								unlockLogs();
								sendBroadcast(
										new Intent()
										.setAction(InformaConstants.Keys.Service.FINISH_ACTIVITY)
										.putExtra(InformaConstants.Keys.ENCRYPTED_IMAGES, imgMap));
							}
						});
					} catch (IllegalArgumentException e) {
						Log.e(InformaConstants.TAG, "informa called Illegal Arguments",e);
						sendBroadcast(
								new Intent()
								.setAction(InformaConstants.Keys.Service.FINISH_ACTIVITY));
					} catch (JSONException e) {
						Log.e(InformaConstants.TAG, "informa called JSONException?",e);
						sendBroadcast(
								new Intent()
								.setAction(InformaConstants.Keys.Service.FINISH_ACTIVITY));
					} catch (IllegalAccessException e) {
						Log.e(InformaConstants.TAG, "informa called Illegal Access",e);
					} catch (NoSuchAlgorithmException e) {
						Log.e(InformaConstants.TAG, "informa called NoSuchAlgoException",e);
					} catch (IOException e) {
						Log.e(InformaConstants.TAG, "informa called IOException",e);
					} catch (NullPointerException e) {
						Log.e(InformaConstants.TAG, "informa called NPE",e);
						sendBroadcast(
								new Intent()
								.setAction(InformaConstants.Keys.Service.FINISH_ACTIVITY));
					}
					
				}
				
			};
				
		}
		new Thread(r).start();
	}
	
	public class Broadcaster extends BroadcastReceiver {
		IntentFilter _filter;
		
		public Broadcaster(IntentFilter filter) {
			_filter = filter;
		}
		
		public JSONArray appendToArray(JSONArray big, JSONArray small) throws JSONException {
			for(int x=0; x<small.length(); x++) {
				big.put(small.get(x));
			}
			return big;
		}

		@Override
		public void onReceive(Context c, Intent i) {
			try {
				if(InformaConstants.Keys.Service.STOP_SERVICE.equals(i.getAction())) {
					unlockLogs();
					stopSucking();
				} else if(BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
					handleBluetooth((BluetoothDevice) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
				} else if(InformaConstants.Keys.Service.SEAL_LOG.equals(i.getAction())) {
					sealLog(
						i.getStringExtra(InformaConstants.Keys.ImageRegion.DATA), 
						i.getStringExtra(InformaConstants.Keys.Image.LOCAL_MEDIA_PATH), 
						i.getLongArrayExtra(InformaConstants.Keys.Intent.ENCRYPT_LIST),
						i.getIntExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO));
				} else if(InformaConstants.Keys.Service.SET_CURRENT.equals(i.getAction())) {
					captureEventData(
						i.getLongExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, 0L),
						i.getIntExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.REGION_GENERATED));
				} else if(InformaConstants.Keys.Service.SET_EXIF.equals(i.getAction())) {
					handleExif(i.getStringExtra(InformaConstants.Keys.Image.EXIF));
				} else if(InformaConstants.Keys.Service.START_SERVICE.equals(i.getAction())) {
					startUpService();
				} else if(InformaConstants.Keys.Service.LOCK_LOGS.equals(i.getAction()))
					lockLogs();
				else if(InformaConstants.Keys.Service.UNLOCK_LOGS.equals(i.getAction()))
					unlockLogs();
				else if(InformaConstants.Keys.Service.INFLATE_VIDEO_TRACK.equals(i.getAction()))
					inflateFromLogs(i.getLongExtra(InformaConstants.Keys.Video.FIRST_TIMESTAMP, 0), i.getLongExtra(InformaConstants.Keys.Video.DURATION, 0));
				
			} catch (Exception e) {
				Log.e(InformaConstants.TAG, "error",e);
			}
		}
			
	}
	
	public interface InformaEncryptor {
		public void informaEncrypt(ArrayList<Map<File, String>> images);
	}
}
