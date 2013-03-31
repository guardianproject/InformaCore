package org.witness.informacam.transport;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IConnection;
import org.witness.informacam.models.IParam;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.HttpUtilityListener;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import info.guardianproject.onionkit.ui.OrbotHelper;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class UploaderService extends Service implements HttpUtilityListener {
	private final IBinder binder = new LocalBinder();
	private static UploaderService uploaderService = null;

	private final static String LOG = App.Transport.LOG; 

	OrbotHelper oh;
	public IPendingConnections pendingConnections = new IPendingConnections();
	private boolean isRunning = false;

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

	@Override
	public void onDestroy() {
		super.onDestroy();
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.UPLOADER_SERVICE).setAction(Actions.DISASSOCIATE_SERVICE));
	}


	public static UploaderService getInstance() {
		return uploaderService;
	}

	private void checkForOrbotStartup() {
		Log.d(LOG, "Orbot has not started yet so i am waiting for it...");
		final Timer t = new Timer();
		TimerTask tt = new TimerTask() {

			public void stop() {
				t.cancel();
				t.purge();
				onOrbotRunning();
			}

			@Override
			public void run() {
				if(oh.isOrbotRunning()) {
					stop();
				}

			}

		};
		t.schedule(tt, 0, 500);
	}

	private void run() {
		if(informaCam.ioService.getBytes(IManifest.PENDING_CONNECTIONS, Type.IOCIPHER) != null && !isRunning) {
			pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());
			if(pendingConnections.queue != null && pendingConnections.queue.size() > 0) {
				isRunning = true;

				for(IConnection connection : pendingConnections.queue) {
					Log.d(LOG, connection.asJson().toString());
					/*
					if(!connection.isHeld) {
						if(connection.method.equals(Models.IConnection.Methods.POST)) {
							connection = HttpUtility.executeHttpPost(connection);
							
						} else if(connection.method.equals(Models.IConnection.Methods.GET)) {
							connection = HttpUtility.executeHttpGet(connection);
						}
						
						if(connection.result.code == Integer.parseInt(Transport.Results.OK)) {
							// TODO: handle the data
						}
						
						if(connection.result.code == Integer.parseInt(Transport.Results.OK) || connection.numTries > 10) {
							// TODO: pop from queue.
						}

						informaCam.saveState(pendingConnections);
					}
					*/
				}
			}
		} else {
			// TODO: start a listener for updates to queue...
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
					new Thread(new Runnable() {
						@Override
						public void run() {
							checkForOrbotStartup();
						}
					}).start();
				}
			});

			return;
		}

		run();
	}

	public void addToQueue(IConnection connection) {
		if(pendingConnections.queue == null) {
			pendingConnections.queue = new ArrayList<IConnection>();
		}

		pendingConnections.queue.add(connection);
	}

	public boolean isConnectedToTor() {
		return oh.isOrbotRunning();
	}

	public void requestCredentials() {
		/*
		 * params: pgpKeyFingerprint
		 * data: publicCredentials
		 * method: post
		 * url: requestUrl
		 * returns: {
		 * 	result:code, 
		 *  data: {
		 *  	_id: id of user, 
		 *  	_rev: _rev of user, 
		 *  	upload_id : id of upload bucket, 
		 *  	upload_rev: rev of upload bucket
		 *  	}
		 *  } or {
		 *   result: code,
		 *   reason: reason as string
		 *  
		 *  }
		 */
		IConnection connection = new IConnection();

	}

	@Override
	public void onOrbotRunning() {
		// TODO Auto-generated method stub
		Log.d(LOG, "Orbot STARTED!  Engage...");
		if(!isRunning) {
			run();
		}
	}


}
