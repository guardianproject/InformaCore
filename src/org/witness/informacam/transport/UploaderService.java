package org.witness.informacam.transport;

import info.guardianproject.onionkit.ui.OrbotHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.models.connections.IPendingConnections;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.connections.IUpload;
import org.witness.informacam.models.credentials.ISecretKey;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IIdentity;
import org.witness.informacam.models.organizations.IInstalledOrganizations;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.HttpUtilityListener;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.InnerBroadcaster;

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

public class UploaderService implements HttpUtilityListener {
	
	private final static String LOG = App.Transport.LOG; 

	OrbotHelper oh;
	public IPendingConnections pendingConnections = null;
	private boolean isRunning = false;
	private int connectionType = -1;

	Timer queueMaster;

	List<InnerBroadcaster> br = new ArrayList<InnerBroadcaster>();
	Handler h = new Handler();

	Context mContext;
	InformaCam mInformaCam;
	
	public UploaderService (Context context, InformaCam informaCam) {
	
		mContext = context;
		mInformaCam = informaCam;
		
		oh = new OrbotHelper(context);

		br.add(new InnerBroadcaster(new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"), android.os.Process.myPid()) {

			@Override
			public void onReceive(Context context, Intent intent) {
				super.onReceive(context, intent);
				if(!isIntended) {
					return;
				}

				if(intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
					Log.d(LOG, "connextivity status changed");
					connectionType = getConnectionStatus();
				}
			}
		});

	}

	public void shutdown (InformaCam informaCam)
	{
		try {
			queueMaster.cancel();
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		unregisterConnectivityUpdates();
		if(pendingConnections != null) {
			informaCam.saveState(pendingConnections);
		}

	}


	private void checkForConnections() {
		queueMaster = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				if(!isRunning) {
					h.post(new Runnable() {
						@Override
						public void run() {
							UploaderService.this.run();
						}
					});
				}
			}
		};
		queueMaster.schedule(tt, 0, (long) ((1000 * 60) * 0.5));
	}

	
	private void registerConnectivityUpdates() {
		for(BroadcastReceiver b : br) {
			mContext.registerReceiver(b, ((InnerBroadcaster) b).intentFilter);
		}
	}

	private void unregisterConnectivityUpdates() {
		for(BroadcastReceiver b : br) {
			try {
				mContext.unregisterReceiver(b);
			} catch(IllegalArgumentException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
	}

	private int getConnectionStatus() {
		ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		try {
			Log.d(LOG, "active network: " + ni.getTypeName());

			return ni.getType();
		} catch(NullPointerException e) {
			return -1;
		}
	}

	private boolean runOrbotCheck() {
		if(!oh.isOrbotInstalled()) {
			h.post(new Runnable() {
				@Override
				public void run() {
				//	oh.promptToInstall(this);

				}
			});

			return false;
		}

		if(!oh.isOrbotRunning()) {
			
			 Intent intent = new Intent(OrbotHelper.URI_ORBOT);
    		 intent.setAction(OrbotHelper.ACTION_START_TOR);
    		 mContext.startActivity(intent);
    		 
    		 /*
			h.post(new Runnable() {
				@Override
				public void run() {
					
					
					
	        		 
					oh.requestOrbotStart(this);this.
					new Thread(new Runnable() {
						@Override
						public void run() {
							checkForOrbotStartup();
						}
					}).start();
				}
			});*/

			return false;
		}

		return true;
	}

	private void run() {
		if(!isRunning) {
			/*
			pendingConnections.queue.clear();
			informaCam.saveState(pendingConnections);
			 */
			Log.d(LOG, "*** STARTING A NEW RUN ***");
			pendingConnections = (IPendingConnections) mInformaCam.getModel(pendingConnections);

			if(pendingConnections.queue != null && pendingConnections.queue.size() > 0) {
				if(!runOrbotCheck()) {
					return;
				}

				isRunning = true;
				registerConnectivityUpdates();
				connectionType = getConnectionStatus();

				new Thread(new Runnable() {
					@Override
					public void run() {
						android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

						//for(Iterator<IConnection> cIt = pendingConnections.queue.iterator(); cIt.hasNext() ;) {
						for(IConnection connection : pendingConnections.queue) {
							//connection.isHeld = false;
							if(!connection.isHeld && !connection.slatedForRemoval) {
								connection.isHeld = true;

								HttpUtility http = new HttpUtility();

								if(connection.type == Models.IConnection.Type.UPLOAD) {
									/*
									IUpload upload = new IUpload(connection);									
									upload.setByteBufferSize(connectionType);
									upload.update();

									connection = upload;
									*/
									((IUpload) connection).setByteBufferSize(connectionType);
									((IUpload) connection).update();
								}

								if(connection.method.equals(Models.IConnection.Methods.POST)) {
									connection = http.executeHttpPost(connection);
								} else if(connection.method.equals(Models.IConnection.Methods.GET)) {
									connection = http.executeHttpGet(connection);
								}

								try {
									if(connection.result.code == Integer.parseInt(Transport.Results.OK)) {
										if(routeResult(connection)) {
											pendingConnections.remove(connection);
											continue;
										}
									} else {
										
										if(connection.result.responseCode == Models.IConnection.ResponseCodes.INVALID_TICKET) {
											pendingConnections.remove(connection);
											continue;
										}

										if(connection.numTries > Models.IConnection.MAX_TRIES) {
											if(!connection.isSticky) {
												pendingConnections.remove(connection);
												continue;
											} else {
												if(connection.result.responseCode == Models.IConnection.ResponseCodes.INVALID_TICKET) {
													pendingConnections.remove(connection);
													continue;
												}
											}
										} else {
											connection.isHeld = false;
										}
									}
								} catch(NullPointerException e) {
									Log.e(LOG, e.toString());
									e.printStackTrace();
								}
							}
						}

						
						pendingConnections.save();
						isRunning = false;
					}
				}).start();
			}
		} else {
			Log.d(LOG, "(exiting; not running)");
		}
	}

	private void initPendingConnections() {
		
		if(mInformaCam.ioService.getBytes(IManifest.PENDING_CONNECTIONS, Type.IOCIPHER) != null) {
			pendingConnections = (IPendingConnections) mInformaCam.getModel(new IPendingConnections());
		} else {
			pendingConnections = new IPendingConnections();
		}
	}

	public void init() {
		isRunning = false;

		if(pendingConnections == null) {
			initPendingConnections();
		}

		if(pendingConnections.queue != null && pendingConnections.queue.size() > 0) {
			for(Iterator<IConnection> cIt = pendingConnections.queue.iterator(); cIt.hasNext(); ) {
				
				IConnection connection = cIt.next();
				connection.isHeld = false;
			}
			
			mInformaCam.saveState(pendingConnections);
		}

		if(!runOrbotCheck()) {
			return;
		}

		run();
		checkForConnections();
	}

	public void addToQueue(IConnection connection) {
		if(pendingConnections == null) {
			initPendingConnections();
		}

		pendingConnections.add(connection);
		if(!this.isRunning) {
			pendingConnections.save();
			run();
		}
	}

	public boolean isConnectedToTor() {
		return oh.isOrbotRunning();
	}

	private boolean routeResult(IConnection connection) {

		IInstalledOrganizations installedOrganizations = (IInstalledOrganizations) mInformaCam.getModel(new IInstalledOrganizations());
		IOrganization organization = installedOrganizations.getByName(connection.destination.organizationName);

		switch(connection.result.responseCode) {
		case Models.IResult.ResponseCodes.UPLOAD_SUBMISSION:
			try {
				if(connection.type == Models.IConnection.Type.SUBMISSION) {
					String uploadId = connection.result.data.getString(Models._ID);
					String uploadRev = connection.result.data.getString(Models._REV);

					IUpload upload = new IUpload(organization, ((ISubmission) connection).pathToNextConnectionData, uploadId, uploadRev);
					upload.associatedNotification = connection.associatedNotification;

					pendingConnections.add(upload);
					return true;
				} else if(connection.type == Models.IConnection.Type.UPLOAD) {
					int bytesReceived = connection.result.data.getInt(Models.IConnection.BYTES_TRANSFERRED_VERIFIED);

					if(connection.result.data.has(Models.IConnection.PROGRESS)) {
						double progress = connection.result.data.getDouble(Models.IConnection.PROGRESS);

						// TODO: update notifications with progress.

						IUpload upload = new IUpload(connection);	
						upload.lastByte = bytesReceived;

						upload.setByteBufferSize(connectionType);
						upload.update();
						upload.isHeld = false;
						upload.associatedNotification = connection.associatedNotification;

						upload.save();
					} else if(connection.result.data.has(Models.IConnection.PARENT)) {
						// TODO:  this is finished.  remove from queue... but persist parent data
						connection.associatedNotification.taskComplete = true;
						mInformaCam.updateNotification(connection.associatedNotification);
						
						return true;
					}
				}
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			break;
		case Models.IResult.ResponseCodes.INIT_USER:										
			try {
				organization.identity = new IIdentity();
				organization.identity.inflate(connection.result.data.getJSONObject(Models.IIdentity.SOURCE));
				mInformaCam.saveState(installedOrganizations);

				INotification notification = new INotification(mContext.getString(R.string.key_sent), mContext.getString(R.string.you_have_successfully_sent, organization.organizationName), Models.INotification.Type.KEY_SENT);
				mInformaCam.addNotification(notification);
				
				return true;
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
					
					ISecretKey secretKey = (ISecretKey) mInformaCam.getModel(new ISecretKey());
					 
					IOrganization mergeOrganization = KeyUtility.installICTD(rawContent, organization, secretKey);
					if(mergeOrganization != null) {
						organization.inflate(mergeOrganization);
						mInformaCam.saveState(installedOrganizations);
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
			return true;
		} else {
			return false;
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
