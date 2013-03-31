package org.witness.informacam.utils;

import org.witness.informacam.utils.Constants.Actions;

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
	public boolean isSubBroadcast = false;
	
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
			
		} else if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
			
		}
		
	}

}
