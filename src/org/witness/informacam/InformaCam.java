package org.witness.informacam;

import info.guardianproject.iocipher.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.witness.informacam.crypto.AesUtility;
import org.witness.informacam.crypto.SignatureService;
import org.witness.informacam.models.ICredentials;
import org.witness.informacam.models.IInstalledOrganizations;
import org.witness.informacam.models.IKeyStore;
import org.witness.informacam.models.IMedia;
import org.witness.informacam.models.IMediaManifest;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.IUser;
import org.witness.informacam.models.Model;
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
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
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
	private List<BroadcastReceiver> informaBroadcasters = new Vector<BroadcastReceiver>();

	public IMediaManifest mediaManifest = new IMediaManifest();
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

	public class LocalBinder extends Binder {
		public InformaCam getService() {
			return InformaCam.this;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(LOG, "InformaCam service started via intent");

		broadcasters.add(new IBroadcaster(new IntentFilter(Actions.SHUTDOWN)));
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
				startService(signatureServiceIntent);
				startService(uploaderServiceIntent);
				startService(ioServiceIntent);
			}
		}).start();

		sendBroadcast(new Intent().setAction(Actions.INFORMACAM_START));
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

		boolean init = false;
		boolean login = false;
		boolean run = false;

		try {
			FileInputStream fis = this.openFileInput(IManifest.USER);			
			if(fis.available() == 0) {
				init = true;
			} else {
				byte[] ubytes = new byte[fis.available()];
				fis.read(ubytes);
				user.inflate(ubytes);
				Log.d(LOG, "CURRENT USER:\n" + user.asJson().toString());

				if(user.isLoggedIn) {
					// test to see if ioCipher is mounted
					if(ioService.isMounted() || attemptLogin()) {
						run = true;
					} else {
						login = true;
					}

				} else {
					login = true;
				}
			}
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			init = true;

		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			init = true;
		}

		if(!user.hasCompletedWizard) {
			init = true;
		}

		Bundle data = new Bundle();

		if(init) {
			// we launch our wizard!			
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Wizard.INIT);
		}

		if(login) {
			// we log in!
			user.isLoggedIn = false;
			ioService.saveBlob(user, new java.io.File(IManifest.USER));
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Login.DO_LOGIN);
		} 

		if(run) {
			byte[] mediaManifestBytes = informaCam.ioService.getBytes(IManifest.MEDIA, Type.IOCIPHER);

			if(mediaManifestBytes != null) {
				mediaManifest.inflate(mediaManifestBytes);
				if(mediaManifest.media != null && mediaManifest.media.size() > 0) {
					for(IMedia m : mediaManifest.media) {
						m.isNew = false;
					}
				}
			}

			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Home.INIT);
		}

		Message message = new Message();
		message.setData(data);
		
		if(a != null) {
			((InformaCamEventListener) a).onUpdate(message);
		}

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
		saveState(mediaManifest, new java.io.File(IManifest.MEDIA));
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
			// XXX: maybe not?
			if(intent.getAction().equals(Actions.SHUTDOWN)) {
				Log.d(LOG, "KILLING IC?");
				shutdown();
			} else if(intent.getAction().equals(Actions.ASSOCIATE_SERVICE)) {				
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
					
					informaBroadcasters.add(new IBroadcaster(new IntentFilter(BluetoothDevice.ACTION_FOUND)));
					informaBroadcasters.add(new IBroadcaster(new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)));
					
					for(BroadcastReceiver br : informaBroadcasters) {
						registerReceiver(br, ((IBroadcaster) br).intentFilter);
					}

					
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
					for(BroadcastReceiver br : informaBroadcasters) {
						unregisterReceiver(br);
					}
					
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
