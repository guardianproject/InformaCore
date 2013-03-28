package org.witness.informacam.utils;

import org.witness.informacam.utils.Constants.Actions;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

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
		}
		
	}

}
