package org.witness.informacam.informa;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.ILog;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App.Informa;
import org.witness.informacam.utils.InformaCamBroadcaster.InformaCamStatusListener;
import org.witness.informacam.utils.InnerBroadcaster;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

public class PersistentService extends InnerBroadcaster implements InformaCamStatusListener {
	ILog iLog = null;
	Intent informaCamIntent = null;
	InformaCam informaCam = null;
	
	private final static String LOG = Informa.LOG;
	
	public PersistentService(Activity a, int processId, ILog iLog) {
		super(new IntentFilter(Actions.PERSISTENT_SERVICE), processId);
		
		this.iLog = iLog;
		set(a);
	}
	
	public void set(Activity a) {
		Log.d(LOG, "I SET UP THE BACKGROUNDER (1 of 2)");
		Context context = a;
		
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		
		Intent intent = new Intent(context, InformaCam.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
		am.cancel(pi);
		
		am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), iLog.autoLogInterval, pi);
		Log.d(LOG, "I SET UP THE BACKGROUNDER (2 of 2)");
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		
		if(!isIntended) {
			return;
		}
		
		
	}

	@Override
	public void onInformaCamStart(Intent intent) {
		// start informa
		informaCam = InformaCam.getInstance();
		Log.d(LOG, "THIS FUCKING LOG " + iLog.asJson().toString());
		iLog.shouldAutoLog = true;
		iLog.save();
		
		informaCam.startInforma();
	}

	@Override
	public void onInformaCamStop(Intent intent) {}

	@Override
	public void onInformaStop(Intent intent) {
		// shutdown informa
		iLog.shouldAutoLog = false;
		iLog.save();
		informaCam.shutdown();
	}

	@Override
	public void onInformaStart(Intent intent) {
		// informa shuts off 5 mins from now
		iLog = new ILog(informaCam.mediaManifest.getById(iLog._id));
		informaCam.informaService.associateMedia(iLog);
		
		(new Handler()).postDelayed(new Runnable() {
			@Override
			public void run() {
				informaCam.stopInforma();
			}
		}, (5 * 60 * 1000));
	}

}
