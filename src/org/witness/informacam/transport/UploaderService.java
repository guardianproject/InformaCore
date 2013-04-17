package org.witness.informacam.transport;

import info.guardianproject.onionkit.ui.OrbotHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.IIdentity;
import org.witness.informacam.models.IInstalledOrganizations;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.connections.IUpload;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.HttpUtilityListener;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
	private int connectionType = -1;

	InformaCam informaCam = InformaCam.getInstance();

	Handler handler = new Handler();
	Timer queueMaster;

	List<Broadcaster> br = new ArrayList<Broadcaster>();

	class Broadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;

		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
				Log.d(LOG, "connextivity status changed");
				connectionType = getConnectionStatus();
			}

		}

	}

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

		br.add(new Broadcaster(new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")));

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

		unregisterConnectivityUpdates();
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
		queueMaster.schedule(tt, 0, (long) ((1000 * 60) * 0.5));
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

	private void registerConnectivityUpdates() {
		for(BroadcastReceiver b : br) {
			registerReceiver(b, ((Broadcaster) b).intentFilter);
		}
	}

	private void unregisterConnectivityUpdates() {
		for(BroadcastReceiver b : br) {
			unregisterReceiver(b);
		}
	}

	private int getConnectionStatus() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		Log.d(LOG, "active network: " + ni.getTypeName());

		return ni.getType();
	}

	private void run() {
		informaCam = InformaCam.getInstance();

		if(informaCam.ioService.getBytes(IManifest.PENDING_CONNECTIONS, Type.IOCIPHER) != null && !isRunning) {
			pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());
			/*
			pendingConnections.queue.clear();
			informaCam.saveState(pendingConnections);
			 */

			if(pendingConnections.queue != null && pendingConnections.queue.size() > 0) {
				isRunning = true;
				registerConnectivityUpdates();
				connectionType = getConnectionStatus();

				new Thread(new Runnable() {
					@Override
					public void run() {
						android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
						for(IConnection connection : pendingConnections.queue) {
							Log.d(LOG, connection.asJson().toString());
							if(!connection.isHeld) {
								connection.isHeld = true;

								Log.d(LOG, connection.asJson().toString());
								HttpUtility http = new HttpUtility();

								if(connection.type == Models.IConnection.Type.UPLOAD) {
									
									((IUpload) connection).setByteBufferSize(connectionType);
								}

								if(connection.method.equals(Models.IConnection.Methods.POST)) {
									connection = http.executeHttpPost(connection);

								} else if(connection.method.equals(Models.IConnection.Methods.GET)) {
									connection = http.executeHttpGet(connection);
								}

								try {
									if(connection.result.code == Integer.parseInt(Transport.Results.OK)) {
										routeResult(connection);
									} else {
										if(connection.numTries > Models.IConnection.MAX_TRIES && !connection.isSticky) {
											pendingConnections.queue.remove(connection);
										} else {
											connection.isHeld = false;
										}
									}
								} catch(NullPointerException e) {
									Log.e(LOG, e.toString());
									e.printStackTrace();
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
		informaCam.saveState(pendingConnections);
		if(!this.isRunning) {
			run();
		}
	}

	public boolean isConnectedToTor() {
		return oh.isOrbotRunning();
	}

	private void routeResult(IConnection connection) {
		IInstalledOrganizations installedOrganizations = (IInstalledOrganizations) informaCam.getModel(new IInstalledOrganizations());
		IOrganization organization = installedOrganizations.getByName(connection.destination.organizationName);

		switch(connection.result.responseCode) {
		case Models.IResult.ResponseCodes.UPLOAD_SUBMISSION:
			try {
				if(connection.type == Models.IConnection.Type.SUBMISSION) {
					String uploadId = connection.result.data.getString(Models._ID);
					String uploadRev = connection.result.data.getString(Models._REV);

					IConnection upload = new IUpload(organization, ((ISubmission) connection).pathToNextConnectionData, uploadId, uploadRev);
					addToQueue(upload);
				} else if(connection.type == Models.IConnection.Type.UPLOAD) {
					((IUpload) connection).update();
				}


				informaCam.saveState(pendingConnections);

			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			break;
		case Models.IResult.ResponseCodes.INIT_USER:										
			try {
				organization.identity = new IIdentity();
				organization.identity.inflate(connection.result.data.getJSONObject(Models.IIdentity.SOURCE));
				informaCam.saveState(installedOrganizations);
				
				INotification notification = new INotification();
				notification.label = informaCam.a.getString(R.string.key_sent);
				notification.content = informaCam.a.getString(R.string.you_have_successfully_sent, organization.organizationName);
				notification.type = Models.INotification.Type.KEY_SENT;
				
				informaCam.addNotification(notification);
				

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

		if(!connection.isSticky) {
			pendingConnections.queue.remove(connection);
		}
	}

	@Override
	public void onOrbotRunning() {
		Log.d(LOG, "Orbot STARTED!  Engage...");
		if(!isRunning) {
			run();
		}
	}


}
