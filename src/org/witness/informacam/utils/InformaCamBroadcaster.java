package org.witness.informacam.utils;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class InformaCamBroadcaster extends BroadcastReceiver {
	private final static String LOG = App.LOG;
	
	public interface InformaCamStatusListener {
		public void onInformaCamStart(Intent intent);
		public void onInformaCamStop(Intent intent);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		InformaCam informaCam = InformaCam.getInstance();
		
		if(intent.getAction().equals(Actions.INFORMACAM_START)) {
			Log.d(LOG, "HEY INFORMACAM START");
			((InformaCamStatusListener) informaCam.a).onInformaCamStart(intent);
			
		} else if(intent.getAction().equals(Actions.INFORMACAM_STOP)) {
			Log.d(LOG, "HEY INFORMACAM STOP");
			((InformaCamStatusListener) informaCam.a).onInformaCamStop(intent);
			
		}
	}

}
