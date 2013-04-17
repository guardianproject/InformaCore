package org.witness.informacam;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.bouncycastle.openpgp.PGPException;
import org.witness.informacam.crypto.AesUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.crypto.SignatureService;
import org.witness.informacam.models.ICredentials;
import org.witness.informacam.models.IInstalledOrganizations;
import org.witness.informacam.models.IKeyStore;
import org.witness.informacam.models.IMediaManifest;
import org.witness.informacam.models.INotificationsManifest;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.ISecretKey;
import org.witness.informacam.models.IUser;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.storage.IOService;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

public class InformaCam extends Service {	
	public final LocalBinder binder = new LocalBinder();
	private final static String LOG = App.LOG;

	private List<BroadcastReceiver> broadcasters = new Vector<BroadcastReceiver>();

	public IMediaManifest mediaManifest = new IMediaManifest();
	public IInstalledOrganizations installedOrganizations = new IInstalledOrganizations();
	public INotificationsManifest notificationsManifest = new INotificationsManifest();
	
	public IUser user;

	Intent ioServiceIntent, signatureServiceIntent, uploaderServiceIntent, informaServiceIntent;

	public UploaderService uploaderService = null;
	public IOService ioService = null;
	public SignatureService signatureService = null;
	public InformaService informaService = null;

	private static InformaCam informaCam;
	public Activity a = null;
	public Handler h = new Handler();


	SharedPreferences.Editor ed;
	private SharedPreferences sp;
	NotificationManager notificationManager;

	public class LocalBinder extends Binder {
		public InformaCam getService() {
			return InformaCam.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(LOG, "ON START COMMAND RECEIVED");
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(LOG, "InformaCam service started via intent");

		broadcasters.add(new IBroadcaster(new IntentFilter(Actions.ASSOCIATE_SERVICE)));
		broadcasters.add(new IBroadcaster(new IntentFilter(Actions.UPLOADER_UPDATE)));
		broadcasters.add(new IBroadcaster(new IntentFilter(Actions.DISASSOCIATE_SERVICE)));

		for(BroadcastReceiver br : broadcasters) {
			registerReceiver(br, ((IBroadcaster) br).intentFilter);
		}

		ioServiceIntent = new Intent(this, IOService.class);
		signatureServiceIntent = new Intent(this, SignatureService.class);
		uploaderServiceIntent = new Intent(this, UploaderService.class);
		informaServiceIntent = new Intent(this, InformaService.class);

		sp = getSharedPreferences(IManifest.PREF, MODE_PRIVATE);
		ed = sp.edit();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(SignatureService.getInstance() != null) {
					signatureService = SignatureService.getInstance();
				} else {
					startService(signatureServiceIntent);
				}
				
				if(UploaderService.getInstance() != null) {
					uploaderService = UploaderService.getInstance();
				} else {
					startService(uploaderServiceIntent);
				}
				
				if(IOService.getInstance() != null) {
					ioService = IOService.getInstance();
				} else {
					startService(ioServiceIntent);
				}
				
				if(signatureService != null && uploaderService != null && ioService != null) {
					startup();
				}
			}
		}).start();

		informaCam = this;
	}

	@SuppressWarnings("deprecation")
	public int[] getDimensions() {
		Display display = a.getWindowManager().getDefaultDisplay();
		Log.d(LOG, "querying window manager for display size: " + display.getWidth() + "," + display.getHeight());
		return new int[] {display.getWidth(),display.getHeight()};
	}

	public void startup() {
		Log.d(LOG, "NOW we init!");
		user = new IUser();

		final int INIT = 1;
		final int LOGIN = 2;
		final int RUN = 3;
		int startCode = LOGIN;

		try {
			FileInputStream fis = this.openFileInput(IManifest.USER);			
			if(fis.available() == 0) {
				startCode = INIT;
			} else {
				byte[] ubytes = new byte[fis.available()];
				fis.read(ubytes);
				user.inflate(ubytes);
				Log.d(LOG, "CURRENT USER:\n" + user.asJson().toString());

				if(user.isLoggedIn) {
					// test to see if ioCipher is mounted
					if(ioService.isMounted() || attemptLogin()) {
						startCode = RUN;
					} else {
						startCode = LOGIN;
					}

				} else {
					startCode = LOGIN;
				}
			}
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			startCode = INIT;

		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			startCode = INIT;
		}

		if(!user.hasCompletedWizard) {
			startCode = INIT;
		}

		Bundle data = new Bundle();

		switch(startCode) {
		case INIT:
			// we launch our wizard!			
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Wizard.INIT);
			break;
		case LOGIN:
			// we log in!
			user.isLoggedIn = false;
			ioService.saveBlob(user, new java.io.File(IManifest.USER));
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Login.DO_LOGIN);
			break;
		case RUN:
			try {
				signatureService.initKey();
			} catch (PGPException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			byte[] mediaManifestBytes = informaCam.ioService.getBytes(IManifest.MEDIA, Type.IOCIPHER);
			
			if(mediaManifestBytes != null) {
				mediaManifest.inflate(mediaManifestBytes);
				if(mediaManifest.media != null && mediaManifest.media.size() > 0) {
					for(IMedia m : mediaManifest.media) {
						m.isNew = false;
					}
				}
			}
			
			byte[] installedOrganizationsBytes = ioService.getBytes(IManifest.ORGS, Type.IOCIPHER);
			if(installedOrganizationsBytes != null) {
				installedOrganizations.inflate(installedOrganizationsBytes);
				
				/*
				for(IOrganization o : installedOrganizations.organizations) {
					o.identity._id = "c4a4638becf85d179b7898169a0331d6";
					o.identity._rev = "3-f01e19014841d00ed365574c5958ec02";
					o.requestUrl = "https://2x7njk43wdexnmwg.onion/";
					o.requestPort = 444;
				}
				
				saveState(installedOrganizations);
				*/
			}
			
			byte[] notificationsManifestBytes = ioService.getBytes(IManifest.NOTIFICATIONS, Type.IOCIPHER);
			if(notificationsManifestBytes != null) {
				notificationsManifest.inflate(notificationsManifestBytes);
				Log.d(LOG, "notifcations" + notificationsManifest.asJson().toString());
				List<INotification> cleanup = new ArrayList<INotification>();
				for(INotification n : notificationsManifest.notifications) {
					if(n.type == 0) {
						cleanup.add(n);
					}
				}
				
				for(INotification n : cleanup) {
					notificationsManifest.notifications.remove(n);
				}
				
				saveState(notificationsManifest);
			}

			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Home.INIT);
			break;
		}

		Intent intent = new Intent(Actions.INFORMACAM_START).putExtra(Codes.Keys.SERVICE, data);
		sendBroadcast(intent);
	}

	private void shutdown() {
		for(BroadcastReceiver br : broadcasters) {
			unregisterReceiver(br);
		}

		ioService.unmount();

		saveStates();

		stopService(ioServiceIntent);
		stopService(signatureServiceIntent);
		stopService(uploaderServiceIntent);

		stopSelf();
	}

	private void saveStates() {
		saveState(user, new java.io.File(IManifest.USER));
		saveState(mediaManifest, new info.guardianproject.iocipher.File(IManifest.MEDIA));
		
	}

	public void saveState(Model model, java.io.File cache) {
		ioService.saveBlob(model.asJson().toString().getBytes(), cache);
		Log.d(LOG, "saved state for " + cache.getAbsolutePath());
	}

	public void saveState(Model model, info.guardianproject.iocipher.File cache) {
		ioService.saveBlob(model.asJson().toString().getBytes(), cache);
		Log.d(LOG, "saved state for " + cache.getAbsolutePath());
	}

	public void saveState(Model model) {
		if(model.getClass().getName().equals(IPendingConnections.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(IManifest.PENDING_CONNECTIONS));
		} else if(model.getClass().getName().equals(IKeyStore.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(IManifest.KEY_STORE_MANIFEST));
		} else if(model.getClass().getName().equals(IInstalledOrganizations.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(IManifest.ORGS));
		} else if(model.getClass().getName().equals(IMediaManifest.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(IManifest.MEDIA));
		} else if(model.getClass().getName().equals(ISecretKey.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(Models.IUser.SECRET));
		} else if(model.getClass().getName().equals(INotificationsManifest.class.getName())) {
			saveState(model, new info.guardianproject.iocipher.File(IManifest.NOTIFICATIONS));
		}
	}

	public Model getModel(Model model) {
		byte[] bytes = null;
		try {
			if(model.getClass().getName().equals(IInstalledOrganizations.class.getName())) {
				bytes = ioService.getBytes(IManifest.ORGS, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(IPendingConnections.class.getName())) {
				bytes = informaCam.ioService.getBytes(IManifest.PENDING_CONNECTIONS, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(IKeyStore.class.getName())) {
				bytes = informaCam.ioService.getBytes(IManifest.KEY_STORE_MANIFEST, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(IMediaManifest.class.getName())) {
				bytes = informaCam.ioService.getBytes(IManifest.MEDIA, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(ISecretKey.class.getName())) {
				bytes = informaCam.ioService.getBytes(Models.IUser.SECRET, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(INotificationsManifest.class.getName())) {
				bytes = informaCam.ioService.getBytes(IManifest.NOTIFICATIONS, Type.IOCIPHER);
			}

			if(bytes != null) {
				model.inflate(bytes);
			} else {
				Log.d(LOG, "BYTES IS NULLLLL");
			}

		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			Log.d(LOG, "bytes is null");
		}

		Log.d(LOG, "model is " + model.getClass().getName());
		return model;

	}

	public void promptForLogin(OnDismissListener odl) {
		AlertDialog.Builder ad = new AlertDialog.Builder(this);
		final Dialog d = ad.create();
		d.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				a.finish();
			}

		});


		if(odl != null) {
			d.setOnDismissListener(odl);
		}

		View view = LayoutInflater.from(this).inflate(R.layout.alert_login, null);
		final EditText password = (EditText) view.findViewById(R.id.login_password);
		final ProgressBar waiter = (ProgressBar) view.findViewById(R.id.login_waiter);

		final Button commit = (Button) view.findViewById(R.id.login_commit);
		commit.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				waiter.setVisibility(View.VISIBLE);
				commit.setVisibility(View.GONE);

				if(attemptLogin(password.getText().toString())) {
					d.dismiss();
				} else {
					waiter.setVisibility(View.GONE);
					commit.setVisibility(View.VISIBLE);
				}

			}

		});

		final Button cancel = (Button) view.findViewById(R.id.login_cancel);
		cancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				d.cancel();
			}

		});

		ad.setView(view);
		ad.show();
	}

	public void promptForLogin() {
		promptForLogin(null);
	}

	public void promptForLogin(final int resumeCode, final byte[] data, final info.guardianproject.iocipher.File file) {
		OnDismissListener odl = new OnDismissListener() {

			@Override
			public void onDismiss(DialogInterface dialog) {
				switch(resumeCode) {
				case Codes.Routes.RETRY_SAVE:
					ioService.saveBlob(data, file);
					break;
				}

			}

		};
		promptForLogin(odl);
	}

	public void persistLogin(String password) {
		ed.putString(Models.IUser.PASSWORD, password).commit();
		ed.putBoolean(Models.IUser.AUTH_TOKEN, true).commit();
	}

	public boolean attemptLogout() {
		user.isLoggedIn = false;
		user.lastLogOut = System.currentTimeMillis();

		ed.remove(Models.IUser.PASSWORD).commit();
		ed.remove(Models.IUser.AUTH_TOKEN).commit();

		shutdown();

		return true;
	}

	public boolean isAbsolutelyLoggedIn() {
		try {
			Log.d(LOG, "status: " + String.valueOf(ioService.isMounted() && user.isLoggedIn));
			return (ioService.isMounted() && user.isLoggedIn);
		} catch(NullPointerException e) {
			return false;
		}
	}

	public boolean attemptLogin() {
		String password = sp.getString(Models.IUser.PASSWORD, null);
		return password == null ? false : attemptLogin(password);

	}

	public boolean attemptLogin(String password) {
		ICredentials credentials = new ICredentials();
		credentials.inflate(ioService.getBytes(Models.IUser.CREDENTIALS, Type.INTERNAL_STORAGE));

		String authToken = AesUtility.DecryptWithPassword(password, credentials.iv.getBytes(), credentials.passwordBlock.getBytes());
		if(authToken != null && ioService.initIOCipher(authToken)) {

			user.inflate(ioService.getBytes(IManifest.USER, Type.INTERNAL_STORAGE));

			user.isLoggedIn = true;
			user.lastLogIn = System.currentTimeMillis();
			ioService.saveBlob(user.asJson().toString().getBytes(), new java.io.File(IManifest.USER));

			persistLogin(password);
			return true;
		}

		return false;
	}

	public void update(Bundle data) {
		Message message = new Message();
		message.setData(data);

		((InformaCamEventListener) a).onUpdate(message);
	}

	public static InformaCam getInstance() {
		Log.d(LOG, "no activity association, just returning instance");
		return informaCam;
	}

	public static InformaCam getInstance(Activity a) {
		informaCam.associateActivity(a);
		Log.d(LOG, "associating to activity " + a.getClass().getName());
		return informaCam;
	}

	public static InformaCam getInstance(FragmentActivity a) {
		informaCam.associateActivity(a);
		Log.d(LOG, "associating to activity " + a.getClass().getName());
		return informaCam;
	}

	public void associateActivity(Activity a) {
		this.a = a;
	}

	public void initUploads() {
		uploaderService.init();
	}
	
	public void addNotification(INotification notification) {
		addNotification(notification, true);
	}
	
	public void addNotification(INotification notification, boolean showOnTop) {
		notificationsManifest.notifications.add(notification);
		saveState(notificationsManifest);
		
		if(showOnTop) {
			Notification n = new NotificationCompat.Builder(a)
				.setContentTitle(notification.label)
				.setContentText(notification.content)
				.setContentIntent(PendingIntent.getActivity(this, 0, null, 0))
				.getNotification();
			
			n.flags |= Notification.FLAG_AUTO_CANCEL;
			notificationManager.notify(0, n);
		}
	}

	public IOrganization installICTD(Uri ictdURI) {
		IOrganization organization = null;

		try {
			InputStream is = getContentResolver().openInputStream(ictdURI);
			byte[] rc = new byte[is.available()];
			is.read(rc);
			is.close();

			organization = KeyUtility.installICTD(rc);
			saveState(installedOrganizations);
			
			INotification notification = new INotification();
			notification.label = a.getString(R.string.key_installed);
			notification.content = a.getString(R.string.x_has_verified_you, organization.organizationName);
			notification.type = Models.INotification.Type.NEW_KEY;
			
			addNotification(notification);
			
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return organization;
	}

	public void startInforma() {
		startService(informaServiceIntent);
	}

	public void stopInforma() {
		stopService(informaServiceIntent);		
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(LOG, "INFORMA CAM SERVICE HAS BEEN DESTROYED");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	private class IBroadcaster extends BroadcastReceiver {
		private final static String LOG = App.LOG;

		IntentFilter intentFilter;

		public IBroadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(Actions.ASSOCIATE_SERVICE)) {				
				switch(intent.getIntExtra(Codes.Keys.SERVICE, 0)) {
				case Codes.Routes.SIGNATURE_SERVICE:
					signatureService = SignatureService.getInstance();
					break;
				case Codes.Routes.IO_SERVICE:
					ioService = IOService.getInstance();
					break;
				case Codes.Routes.UPLOADER_SERVICE:
					uploaderService = UploaderService.getInstance();
					break;
				case Codes.Routes.INFORMA_SERVICE:
					informaService = InformaService.getInstance();
					break;
				}

				if(signatureService == null) {
					Log.d(LOG, "cannot init yet (signature) ... trying again");
					return;
				}

				if(uploaderService == null) {
					Log.d(LOG, "cannot init yet (uploader) ... trying again");
					return;
				}

				if(ioService == null) {
					Log.d(LOG, "cannot init yet (io) ... trying again");
					return;
				}

				if(intent.getIntExtra(Codes.Keys.SERVICE, 0) != Codes.Routes.INFORMA_SERVICE) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							startup();
						}
					}).start();
				}

			} else if(intent.getAction().equals(Actions.DISASSOCIATE_SERVICE)) {
				switch(intent.getIntExtra(Codes.Keys.SERVICE, 0)) {
				case Codes.Routes.INFORMA_SERVICE:
					informaService = null;
					sendBroadcast(new Intent().setAction(Actions.INFORMA_STOP));
					break;
				}
			} else if(intent.getAction().equals(Actions.UPLOADER_UPDATE)) {
				switch(intent.getIntExtra(Codes.Keys.UPLOADER, 0)) {
				case Codes.Transport.MUST_INSTALL_TOR:
					break;
				case Codes.Transport.MUST_START_TOR:
					break;
				}
			}
		}

	}
}
