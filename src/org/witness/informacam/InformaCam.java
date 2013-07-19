package org.witness.informacam;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import org.bouncycastle.openpgp.PGPException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.crypto.CredentialManager;
import org.witness.informacam.crypto.SignatureService;
import org.witness.informacam.informa.Cron;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.credentials.IKeyStore;
import org.witness.informacam.models.credentials.ISecretKey;
import org.witness.informacam.models.credentials.IUser;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.j3m.IDCIMDescriptor;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IMediaManifest;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.notifications.INotificationsManifest;
import org.witness.informacam.models.organizations.IInstalledOrganizations;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.models.utils.ILanguageMap;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.storage.FormUtility;
import org.witness.informacam.storage.IOService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.ListAdapterListener;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.InformaCamBroadcaster.InformaCamStatusListener;
import org.witness.informacam.utils.InnerBroadcaster;
import org.witness.informacam.utils.TransportUtility;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import dalvik.system.DexFile;

public class InformaCam extends Application {	
	
	private final static String LOG = App.LOG;

	private List<InnerBroadcaster> broadcasters = new Vector<InnerBroadcaster>();

	public IMediaManifest mediaManifest = new IMediaManifest();
	public IInstalledOrganizations installedOrganizations = new IInstalledOrganizations();
	public INotificationsManifest notificationsManifest = new INotificationsManifest();
	public ILanguageMap languageMap = new ILanguageMap();
	
	public IUser user;

	Intent ioServiceIntent, signatureServiceIntent, uploaderServiceIntent, informaServiceIntent;

	public IOService ioService = null;
	public SignatureService signatureService = null;
	public InformaService informaService = null;

	public Handler h = new Handler();

	public List<String> models = new ArrayList<String>();

	NotificationManager notificationManager;
	
	private int processId;

	private static InformaCam mInstance = null;
	
	private ListAdapterListener mListAdapterListener = null;
	private InformaCamStatusListener mStatusListener = null;
	private InformaCamEventListener mEventListener = null;
	
	private CredentialManager credentialManager = null;
	private PendingIntent cronPI = null;
	
	
	//we really need to get rid of this
	public static InformaCam getInstance ()
	{
		return mInstance;
	}
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mInstance = this;
		processId = android.os.Process.myPid();
		
		Log.d(LOG, "InformaCam service started via intent");

		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		broadcasters.add(new InnerBroadcaster(new IntentFilter(Actions.ASSOCIATE_SERVICE), processId) {
			@Override
			public void onReceive(Context context, Intent intent) {
				super.onReceive(context, intent);
				if(!isIntended) {
					return;
				}
				
				if(intent.getAction().equals(Actions.ASSOCIATE_SERVICE)) {
					int serviceCode = intent.getIntExtra(Codes.Keys.SERVICE, 0);
					
					switch(serviceCode) {
					case Codes.Routes.INFORMA_SERVICE:
						informaService = InformaService.getInstance();
						break;
					}
				} 
			}
		});
		
		broadcasters.add(new InnerBroadcaster(new IntentFilter(Actions.UPLOADER_UPDATE), processId) {
			@Override
			public void onReceive(Context context, Intent intent) {
				super.onReceive(context, intent);
				if(!isIntended) {
					return;
				}
				
				if(intent.getAction().equals(Actions.UPLOADER_UPDATE)) {
					switch(intent.getIntExtra(Codes.Keys.UPLOADER, 0)) {
					case Codes.Transport.MUST_INSTALL_TOR:
						break;
					case Codes.Transport.MUST_START_TOR:
						break;
					}
				}
			}
		});
			
		broadcasters.add(new InnerBroadcaster(new IntentFilter(Actions.DISASSOCIATE_SERVICE), processId) {
			@Override
			public void onReceive(Context context, Intent intent) {
				super.onReceive(context, intent);
				if(!isIntended) {
					return;
				}
				
				if(intent.getAction().equals(Actions.DISASSOCIATE_SERVICE)) {
					switch(intent.getIntExtra(Codes.Keys.SERVICE, 0)) {
					case Codes.Routes.INFORMA_SERVICE:
						informaService = null;
						sendBroadcast(new Intent()
							.setAction(Actions.INFORMA_STOP)
							.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, processId));
						break;
					}
				}
			}
		});

		for(BroadcastReceiver br : broadcasters) {
			registerReceiver(br, ((InnerBroadcaster) br).intentFilter);
		}

		informaServiceIntent = new Intent(this, InformaService.class);

		signatureService = new SignatureService(InformaCam.this);
		ioService = new IOService(InformaCam.this);
		
		new Thread ()
		{
			
			public void run ()
			{
		
				startup();
			}
		}.start();
		
		
	}

	public void startup() {
		Log.d(LOG, "NOW we init!");
		user = new IUser();
		
		setModels();

		final int INIT = 1;
		final int LOGIN = 2;
		final int RUN = 3;
		int startCode = LOGIN;

		try {
			FileInputStream fis = this.openFileInput(IManifest.USER);			
			if(fis.available() == 0) {
				startCode = INIT;
			} else {
				setCredentialManager(new CredentialManager(this, !ioService.isMounted()));
				
				byte[] ubytes = new byte[fis.available()];
				fis.read(ubytes);
				user.inflate(ubytes);
				
				if(credentialManager.getStatus() == Codes.Status.UNLOCKED) {
					startCode = RUN;
				} else if(credentialManager.getStatus() == Codes.Status.LOCKED) {
					startCode = LOGIN;
				}
			}
		} catch (FileNotFoundException e) {
			Log.e(LOG, "CONSIDERED HANDLED:\n" + e.toString(),e);
			startCode = INIT;

		} catch (IOException e) {
			Log.e(LOG, e.toString(),e);
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
			try
			{
				ioService.saveBlob(user, new java.io.File(IManifest.USER));
				data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Login.DO_LOGIN);
			}
			catch (IOException ioe)
			{
				Log.e(LOG, ioe.toString(),ioe);
				startCode = INIT;	
			}
			break;
		case RUN:
			java.io.File icDump = new java.io.File(Storage.EXTERNAL_DIR);
			if(!icDump.exists()) {
				icDump.mkdir();
			}
			
			user.isInOfflineMode = false;
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Home.INIT);
			break;
		}

		Intent intent = new Intent(Actions.INFORMACAM_START)
			.putExtra(Codes.Keys.SERVICE, data)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, processId);
		sendBroadcast(intent);
	}
	
	public void initData() {
		try {
			signatureService.initKey( (ISecretKey) getModel(new ISecretKey()));
		} catch (PGPException e) {
			Log.e(LOG, e.toString(),e);
		}
		
		mediaManifest = (IMediaManifest) getModel(mediaManifest);
		if(mediaManifest.getMediaList().size() > 0) {
			for(IMedia m : mediaManifest.getMediaList()) {
				m.isNew = false;
			}
		}
		
		installedOrganizations = (IInstalledOrganizations) getModel(installedOrganizations);
		
		notificationsManifest = (INotificationsManifest) getModel(notificationsManifest);
		if(notificationsManifest.notifications.size() > 0) {
			notificationsManifest.sortBy(Models.INotificationManifest.Sort.DATE_DESC);
		
			saveState(notificationsManifest);
			
			
		}
		
		languageMap = (ILanguageMap) getModel(languageMap);
	}
	
	private void setModels() {
		
		try {
			String apkName = getPackageResourcePath();
			java.io.File dexDump = getDir(IManifest.DEX, Context.MODE_PRIVATE);
			if(!dexDump.exists()) {
				dexDump.mkdir();
			}
			DexFile dex = DexFile.loadDex(apkName, new java.io.File(dexDump, "dex").getAbsolutePath(), 0);
			for(Enumeration<String> classes = dex.entries(); classes.hasMoreElements() ;) {
				String clz = classes.nextElement();
				if(clz.contains("org.witness.informacam.models")) {
					models.add(clz);
				}
			}
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public void shutdown() {
		for(BroadcastReceiver br : broadcasters) {
			try {
				unregisterReceiver(br);
			} catch(IllegalArgumentException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		saveStates();
			
		ioService.unmount();
				
		if(informaService != null) {
			stopService(informaServiceIntent);
		}
		
		Intent intent = new Intent(Actions.INFORMACAM_STOP)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, processId);
		
		sendBroadcast(intent);
		

	}

	public void saveStates() {
		try {
			saveState(user, new java.io.File(IManifest.USER));
		
			saveState(mediaManifest, new info.guardianproject.iocipher.File(IManifest.MEDIA));
		
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString(),e);
		}
		catch(IOException e) {
			Log.e(LOG, e.toString(),e);
		}
		
	}

	public boolean saveState(Model model, java.io.File cache) throws IOException {
		return ioService.saveBlob(model.asJson().toString().getBytes(), cache);
	}

	public boolean saveState(Model model, info.guardianproject.iocipher.File cache) throws IOException {
		return ioService.saveBlob(model.asJson().toString().getBytes(), cache);
	}

	public boolean saveState(Model model) {		
		
		try
		{
			if(model.getClass().getName().equals(IKeyStore.class.getName())) {
				
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.KEY_STORE_MANIFEST));
			
				
			} else if(model.getClass().getName().equals(IInstalledOrganizations.class.getName())) {
			
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.ORGS));
			
			} else if(model.getClass().getName().equals(IMediaManifest.class.getName())) {
				
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.MEDIA));
				
			} else if(model.getClass().getName().equals(ISecretKey.class.getName())) {
			
				return saveState(model, new info.guardianproject.iocipher.File(Models.IUser.SECRET));
			
			} else if(model.getClass().getName().equals(INotificationsManifest.class.getName())) {
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.NOTIFICATIONS));
				
			} else if(model.getClass().getName().equals(IDCIMDescriptor.class.getName())) {
				
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.DCIM));
				
			} else if(model.getClass().getName().equals(IUser.class.getName())) {
				
				return ioService.saveBlob(model, new java.io.File(IManifest.USER));
			} else if(model.getClass().getName().equals(ILanguageMap.class.getName())) {
				return saveState(model, new info.guardianproject.iocipher.File(IManifest.LANG));
			}
		}
		catch (IOException ioe)
		{
			Log.e(LOG,"saveState(): error writing mode to iocipher",ioe);
		}
		
		return false;
	}

	public Model getModel(Model model) {
		byte[] bytes = null;
		try {
			if(model.getClass().getName().equals(IInstalledOrganizations.class.getName())) {
				bytes = ioService.getBytes(IManifest.ORGS, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(IKeyStore.class.getName())) {
				bytes = ioService.getBytes(IManifest.KEY_STORE_MANIFEST, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(IMediaManifest.class.getName())) {
				bytes = ioService.getBytes(IManifest.MEDIA, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(ISecretKey.class.getName())) {
				bytes = ioService.getBytes(Models.IUser.SECRET, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(INotificationsManifest.class.getName())) {
				bytes = ioService.getBytes(IManifest.NOTIFICATIONS, Type.IOCIPHER);
			} else if(model.getClass().getName().equals(ILanguageMap.class.getName())) {
				bytes = ioService.getBytes(IManifest.LANG, Type.IOCIPHER);
			}

			if(bytes != null) {
				model.inflate(bytes);
			}

		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			Log.d(LOG, "bytes is null");
		}

		return model;

	}


	public boolean attemptLogout() {
		if(credentialManager.logout()) {
			shutdown();
			return true;
		}
		
		return false;
	}

	public boolean attemptLogin(String password) {
		return credentialManager.login(password);
	}
	
	public int getProcess() {
		return processId;
	}

	public void update(Bundle data) {
		Message message = new Message();
		message.setData(data);

		if (mEventListener != null)
			mEventListener.onUpdate(message);
	}

	public void updateNotification(INotification notification) {
		notificationsManifest.getById(notification._id).inflate(notification.asJson());
		
		saveState(notificationsManifest);
		
		if (mListAdapterListener != null)
			mListAdapterListener.updateAdapter(Codes.Adapters.NOTIFICATIONS);
		
	}
	
	
	public void addNotification(INotification notification) {
		addNotification(notification, false, null);
	}
	
	public void addNotification(INotification notification, Handler callback) {
		addNotification(notification, false, callback);
	}
	
	public void addNotification(INotification notification, boolean showOnTop, Handler callback) {
		if(notificationsManifest.notifications == null) {
			notificationsManifest.notifications = new ArrayList<INotification>();
		}
		
		notificationsManifest.notifications.add(notification);
		
		saveState(notificationsManifest);
		
		if(callback != null) {
			Message msg = new Message();
			Bundle msgData = new Bundle();
			
			msgData.putInt(Models.INotification.CLASS, Models.INotification.Type.NEW_KEY);
			msgData.putInt(Models.INotification.ID, notificationsManifest.notifications.indexOf(notification));
			msg.setData(msgData);
			callback.sendMessage(msg);
			
		}
	}

	public void setListAdapterListener (ListAdapterListener lal)
	{
		mListAdapterListener = lal;
	}
	
	public void setStatusListener (InformaCamStatusListener sl)
	{
		mStatusListener = sl;
	}
	
	public InformaCamStatusListener getStatusListener ()
	{
		return mStatusListener;
	}
	
	public void setEventListener (InformaCamEventListener sl)
	{
		mEventListener = sl;
	}
	
	public InformaCamEventListener getEventListener ()
	{
		return mEventListener;
	}
	
	public IOrganization installICTD(JSONObject ictd, Handler callback) {
		IOrganization organization = null;
		JSONArray forms = null;
		
		try {
			forms = (JSONArray) ictd.get(Models.IOrganization.FORMS);
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		if(forms != null) {
			for(int f=0; f<forms.length(); f++) {
				try {
					ByteArrayOutputStream baos = IOUtility.unGZipBytes(Base64.decode(forms.getString(f).getBytes(), Base64.DEFAULT));
					IForm form = FormUtility.importAndParse(new ByteArrayInputStream(baos.toByteArray()));
					if(form != null) {
						forms.put(f, form.asJson());
					}
					baos.close();
				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				} catch (IOException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
			}
		}
		
		
		try {
			ByteArrayOutputStream baos = IOUtility.unGZipBytes(Base64.decode(((String) ictd.get(Models.IOrganization.PUBLIC_KEY)).getBytes(), Base64.DEFAULT));
			info.guardianproject.iocipher.File publicKeyFile = new info.guardianproject.iocipher.File(Storage.ORGS_ROOT, ictd.getString(Models.IOrganization.ORGANIZATION_FINGERPRINT) + ".asc");
			Log.d(LOG, new String(baos.toByteArray()));
			if(InformaCam.getInstance().ioService.saveBlob(baos.toByteArray(), publicKeyFile)) {
				ictd.put(Models.IOrganization.PUBLIC_KEY, publicKeyFile.getAbsolutePath());
			}
			baos.close();
			
			organization = new IOrganization(ictd);
			Log.d(LOG, "HERE IS NEW ORG:\n" + organization.asJson().toString());
		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		
		if(organization != null) {
			installedOrganizations.organizations.add(organization);
			saveState(installedOrganizations);
		
			INotification notification = new INotification(getString(R.string.key_installed), getString(R.string.x_has_verified_you, organization.organizationName), Models.INotification.Type.NEW_KEY);			
			addNotification(notification, callback);
		}
		
		return organization;
	}
	
	public IOrganization installICTD(Uri ictdURI, Handler callback) {

		try {
			InputStream is = getContentResolver().openInputStream(ictdURI);
			byte[] rc = new byte[is.available()];
			is.read(rc);
			is.close();

			return installICTD((JSONObject) new JSONTokener(new String(rc)).nextValue(), callback); 
			
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (SecurityException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return null;
	}

	public void startInforma() {
		startService(informaServiceIntent);
	}

	public void stopInforma() {
		stopService(informaServiceIntent);		
	}

	
	@Override
	public void onTerminate() {
		super.onTerminate();
		
		this.shutdown();
		credentialManager.logout();
		
	}
	
	public void exportCredentials() {
		java.io.File credentials = getPublicCredentials();
		Intent intent = new Intent()
			.setAction(Intent.ACTION_SEND)
			.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(credentials))
			.setType("file/");

		startActivity(Intent.createChooser(intent, getString(R.string.send)));

	}
	
	public void resendCredentials(IOrganization organization) {
		ITransportStub transportStub = new ITransportStub(organization);
		transportStub.setAsset(Models.IUser.PUBLIC_CREDENTIALS, Models.IUser.PUBLIC_CREDENTIALS, MimeType.ZIP);
		TransportUtility.initTransport(transportStub);
	}
	
	public boolean hasCredentialManager() {
		if(credentialManager != null) {
			return true;
		}
		
		return false;
	}
	
	public void setCredentialManager(CredentialManager credentialManager) {
		this.credentialManager = credentialManager;
		this.credentialManager.onResume();		
	}
	
	public java.io.File getPublicCredentials() {
		return ioService.getPublicCredentials();
	}
	
	public int getCredentialManagerStatus() {
		return credentialManager.getStatus();
	}
	
	public void startCron() {
		Intent startCron = new Intent(this, Cron.class);
		cronPI = PendingIntent.getService(this, 0, startCron, 0);
		
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, 30);
		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 30 * 1000, cronPI);
	}
	
	public void stopCron() {
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(cronPI);
	}
}
