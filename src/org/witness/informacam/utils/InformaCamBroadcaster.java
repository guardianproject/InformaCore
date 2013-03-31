package org.witness.informacam.utils;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.suckers.PhoneSucker;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.Constants.Suckers.Phone;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;

public class InformaCamBroadcaster extends BroadcastReceiver {
	Activity a;
	public IntentFilter intentFilter;
	
	public interface InformaCamStatusListener {
		public void onInformaCamStart();
		public void onInformaCamStop();
	}
	
	public InformaCamBroadcaster(Activity a, IntentFilter intentFilter) {
		this.a = a;
		this.intentFilter = intentFilter;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if(intent.getAction().equals(Actions.INFORMACAM_START)) {
			((InformaCamStatusListener) a).onInformaCamStart();
		} else if(intent.getAction().equals(Actions.INFORMACAM_STOP)) {
			((InformaCamStatusListener) a).onInformaCamStop();
		} else if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
			
			try {
				InformaService informaService = InformaCam.getInstance().informaService;
				
				BluetoothDevice bd = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				LogPack logPack = new LogPack(Phone.Keys.BLUETOOTH_DEVICE_ADDRESS, bd.getAddress());
				logPack.put(Phone.Keys.BLUETOOTH_DEVICE_NAME, bd.getName());
				((SuckerCacheListener) informaService).onUpdate(logPack);
				
			} catch(JSONException e) {}
		} else if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
			InformaService informaService = InformaCam.getInstance().informaService;
			
			LogPack logPack = new LogPack(Phone.Keys.VISIBLE_WIFI_NETWORKS, ((PhoneSucker) informaService._phone).getWifiNetworks());
			((SuckerCacheListener) informaService).onUpdate(logPack);
		}
		
	}

}
