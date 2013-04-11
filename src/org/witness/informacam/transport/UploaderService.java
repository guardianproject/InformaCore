package org.witness.informacam.transport;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.IIdentity;
import org.witness.informacam.models.IInstalledOrganizations;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.HttpUtilityListener;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models.IUser;

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
	Timer queueMaster;

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
		try {
			queueMaster.cancel();
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		informaCam.saveState(pendingConnections);
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.UPLOADER_SERVICE).setAction(Actions.DISASSOCIATE_SERVICE));
	}

	public static UploaderService getInstance() {
		return uploaderService;
	}
	
	private void checkForConnections() {
		queueMaster = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				if(!isRunning) {
					UploaderService.this.run();
				}
			}
		};
		queueMaster.schedule(tt, 0, (1000 * 60) * 10);
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
				
				new Thread(new Runnable() {
					@Override
					public void run() {
						for(IConnection connection : pendingConnections.queue) {
							if(!connection.isHeld) {
								connection.isHeld = true;
								
								Log.d(LOG, connection.asJson().toString());
								HttpUtility http = new HttpUtility();

								if(connection.method.equals(Models.IConnection.Methods.POST)) {
									connection = http.executeHttpPost(connection);

								} else if(connection.method.equals(Models.IConnection.Methods.GET)) {
									connection = http.executeHttpGet(connection);
								}

								if(connection.result.code == Integer.parseInt(Transport.Results.OK)) {
									routeResult(connection);
								} else {
									if(connection.numTries > Models.IConnection.MAX_TRIES && !connection.isSticky) {
										pendingConnections.queue.remove(connection);
									} else {
										connection.isHeld = false;
									}
								}

								informaCam.saveState(pendingConnections);
							}
						}
						isRunning = false;

					}
				}).start();
			}
		}
	}

	public void init() {
		isRunning = false;
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
		checkForConnections();
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

	private void routeResult(IConnection connection) {
		switch(connection.result.responseCode) {
		case Models.IResult.ResponseCodes.INIT_USER:										
			try {
				IInstalledOrganizations installedOrganizations = (IInstalledOrganizations) informaCam.getModel(new IInstalledOrganizations());
				IOrganization organization = installedOrganizations.getByName(connection.destination.organizationName);
				organization.identity = new IIdentity();
				organization.identity.inflate(connection.result.data.getJSONObject(Models.IIdentity.SOURCE));
				informaCam.saveState(installedOrganizations);
				
				/*
				 * XXX: we don't like this.
				IConnection nextConnection = new IConnection();
				nextConnection.knownCallback = Models.IResult.ResponseCodes.DOWNLOAD_ASSET;
				
				IParam user = new IParam();
				user.key = IUser.BELONGS_TO_USER;
				user.value = organization.identity._id;
				
				nextConnection.params = new ArrayList<IParam>();
				nextConnection.params.add(user);
				
				String exportId = connection.result.data.getJSONObject(Models.IIdentity.CREDENTIALS).getString(Models._ID);
				nextConnection.url = (connection.url + Models.IConnection.Routes.EXPORT + exportId);
				nextConnection.port = connection.port;
				nextConnection.destination = connection.destination;
				
				// this connection should be sticky; user should retry their credential download until it is available.
				nextConnection.isSticky = true;
				
				
				addToQueue(nextConnection);
				informaCam.saveState(pendingConnections);
				*/
				
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			break;
		case Models.IResult.ResponseCodes.DOWNLOAD_ASSET:
			try {
				String rawContent = connection.result.data.getString(Models.IResult.CONTENT);
				switch(connection.knownCallback) {
				case Models.IResult.ResponseCodes.INSTALL_ICTD:
					
						IInstalledOrganizations installedOrganizations = (IInstalledOrganizations) informaCam.getModel(new IInstalledOrganizations());
						IOrganization organization = installedOrganizations.getByName(connection.destination.organizationName);
						IOrganization mergeOrganization = KeyUtility.installICTD(rawContent, organization);
						if(mergeOrganization != null) {
							organization.inflate(mergeOrganization);
							informaCam.saveState(installedOrganizations);
						}
					
					break;
				}
				
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			break;
		}
		
		pendingConnections.queue.remove(connection);
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
