package org.witness.informacam.informa;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.MainActivity;
import org.witness.informacam.crypto.SignatureUtility;
import org.witness.informacam.informa.SensorLogger.OnSuckerUpdateListener;
import org.witness.informacam.informa.suckers.AccelerometerSucker;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Crypto.Signatures;
import org.witness.informacam.utils.Constants.Informa.Keys;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Phone;

import com.google.common.cache.Cache;
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

public class InformaService extends Service implements OnSuckerUpdateListener {
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
	
	public int getStatus() {
		return informaCurrentStatus;
	}
	
	@Override
	public void onCreate() {
		Log.d(Constants.Informa.LOG, "InformaService running");
		
		toMainActivity = new Intent(this, MainActivity.class);
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);		
		init();
		informaService = this;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(Constants.Informa.LOG, "InformaService stopped");
	}
		
	@SuppressWarnings({"unchecked","deprecation"})
	private void init() {
		br.add(new Broadcaster(new IntentFilter(BluetoothDevice.ACTION_FOUND)));
		
		for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b).intentFilter);
		
		suckerCache = CacheBuilder.newBuilder()
				.maximumSize(20000L)
				.build(new CacheLoader<Long, LogPack>() {
					@Override
					public LogPack load(Long timestamp) throws Exception {
						
						return suckerCache.get(timestamp);
					}
				});
		
		_geo = new GeoSucker(InformaService.this);
		_phone = new PhoneSucker(InformaService.this);
		_acc = new AccelerometerSucker(InformaService.this);
	}
	
	@SuppressWarnings("unused")
	private void doShutdown() {
		_geo.getSucker().stopUpdates();
		_phone.getSucker().stopUpdates();
		_acc.getSucker().stopUpdates();
		
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
	
	private void pushToSucker(SensorLogger<?> sucker, LogPack logPack) throws JSONException {
		if(sucker.getClass().equals(PhoneSucker.class))
			_phone.sendToBuffer(logPack);
	}

	@Override
	public void onSuckerUpdate(long timestamp, final LogPack logPack) {
		try {
			Log.d(Suckers.LOG, timestamp + " :\n" + logPack.toString());
			ex = Executors.newFixedThreadPool(100);
			Future<String> sig = ex.submit(new Callable<String>() {

				@Override
				public String call() throws Exception {
					return SignatureUtility.getInstance().signData(logPack.toString().getBytes());
				}
				
			});
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