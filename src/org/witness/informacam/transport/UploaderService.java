package org.witness.informacam.transport;

import java.util.Vector;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.models.IConnection;
import org.witness.informacam.utils.models.IPendingConnections;

import info.guardianproject.onionkit.ui.OrbotHelper;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class UploaderService extends Service {
	private final IBinder binder = new LocalBinder();
	private static UploaderService uploaderService = null;
	
	private final static String LOG = App.Transport.LOG; 
	
	OrbotHelper oh;
	public IPendingConnections pendingConnections = new IPendingConnections();
	
	InformaCam informaCam = InformaCam.getInstance();
	
	Handler handler = new Handler();
	
	Runnable connectionRunnable = new Runnable() {
		@Override
		public void run() {
			
		}
	};
	
	public class LocalBinder extends Binder {
		public UploaderService getService() {
			return UploaderService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		oh = new OrbotHelper(this);

		uploaderService = this;
		sendBroadcast(new Intent().setAction(Actions.ASSOCIATE_SERVICE).putExtra(Codes.Keys.SERVICE, Codes.Routes.UPLOADER_SERVICE));
	}
	
	public static UploaderService getInstance() {
		return uploaderService;
	}
	
	private void run() {
		if(informaCam.ioService.getBytes(Models.IPendingConnections.PATH, Type.IOCIPHER) != null) {
			pendingConnections = new IPendingConnections();
			pendingConnections.inflate(informaCam.ioService.getBytes(Models.IPendingConnections.PATH, Type.IOCIPHER));
			
			for(IConnection connection : pendingConnections.queue) {
				if(!connection.isHeld) {
					
				}
			}			
		}
	}
	
	public void init() {
		if(!oh.isOrbotInstalled()) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					oh.promptToInstall(informaCam.a);
				}
			});
			
			return;
		}
		
		if(!oh.isOrbotRunning()) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					oh.requestOrbotStart(informaCam.a);
				}
			});
			
			return;
		}
		
		run();
	}
	
	public void addToQueue(IConnection connection) {
		if(pendingConnections.queue == null) {
			pendingConnections.queue = new Vector<IConnection>();
		}
		
		pendingConnections.queue.add(connection);
	}
	
	public boolean checkForOrbot(Activity a) {
		if(oh.isOrbotInstalled()) {
			return true;
		} else {
			oh.promptToInstall(a);
			return false;
		}
	}
	
	public boolean isConnectedToTor() {
		return oh.isOrbotRunning();
	}
	
	
	

}
