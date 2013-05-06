package org.witness.informacam.models.media;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IData;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntent;
import org.witness.informacam.models.j3m.ISensorCapture;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.TimeUtility;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

public class ILog extends IMedia {
	public long autoLogInterval = 10 * (60 * 1000);	// 10 minutes?
	public boolean shouldAutoLog = false;

	public long startTime = 0L;
	public long endTime = 0L;

	public List<IMedia> attachedMedia = null;
	public IForm attachedForm = null;
	public String formPath = null;

	private Handler proxyHandler;

	public ILog() {
		super();
		
		dcimEntry = new IDCIMEntry();
		dcimEntry.mediaType = MimeType.LOG;
	}

	public ILog(IMedia media) {
		super();
		inflate(media.asJson());
	}
	
	public static ILog getLogByDay(long timestamp) {
		ILog iLog = null;
		List<IMedia> availableLogs = InformaCam.getInstance().mediaManifest.getAllByType(MimeType.LOG);
		for(IMedia l : availableLogs) {
			ILog log = new ILog(l);
			if(TimeUtility.matchesDay(timestamp, log.startTime)) {
				iLog = log;
				break;
			}
		}
		
		return iLog;
	}

	@Override
	public boolean export(Handler h) {
		return export(h, null, true);
	}

	@Override
	public boolean export(Handler h, IOrganization organization) {
		return export(h, organization, false);
	}

	@SuppressLint("HandlerLeak")
	@Override
	public boolean export(Handler h, IOrganization organization, boolean share) {
		Log.d(LOG, "exporting a log!");
		proxyHandler = h;
		responseHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				Bundle b = msg.getData();
				if(b.containsKey(Models.IMedia.VERSION)) {
					try {
						JSONObject versionManifest = new JSONObject();
						versionManifest.put("absolutePath", b.getString(Models.IMedia.VERSION));
						versionManifest.put("name", b.getString(Models.IMedia.VERSION).substring(b.getString(Models.IMedia.VERSION).lastIndexOf("/")));
						getJSONArray(Models.IMedia.ILog.ATTACHED_MEDIA).put(versionManifest);
					} catch (JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
			}
		};

		int progress = 0;

		InformaCam informaCam = InformaCam.getInstance();
		Map<String, byte[]> j3mZip = new HashMap<String, byte[]>();

		INotification notification = new INotification();
		// its icon will probably be some sort of stock thing

		// create its data
		if(data == null) {
			data = new IData();
		}
		progress += 5;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		if(associatedCaches != null && associatedCaches.size() > 0) { 
			for(String ac : associatedCaches) {
				try {
					// get the data and loop through capture types
					byte[] c = informaCam.ioService.getBytes(ac, Type.IOCIPHER);
					JSONArray cache = ((JSONObject) new JSONTokener(new String(c)).nextValue()).getJSONArray(Models.LogCache.CACHE);

					for(int i=0; i<cache.length(); i++) {
						JSONObject entry = cache.getJSONObject(i);
						long ts = Long.parseLong((String) entry.keys().next());

						JSONObject captureEvent = entry.getJSONObject(String.valueOf(ts));

						Log.d(LOG, "this entry: " + entry.toString());

						JSONArray captureTypes = captureEvent.getJSONArray(CaptureEvent.Keys.TYPE);

						for(int ct=0; ct<captureTypes.length(); ct++) {
							switch((Integer) captureTypes.get(ct)) {
							case CaptureEvent.SENSOR_PLAYBACK:
								if(data.sensorCapture == null) {
									data.sensorCapture = new ArrayList<ISensorCapture>();
								}

								data.sensorCapture.add(new ISensorCapture(ts, captureEvent));							
								break;
							case CaptureEvent.REGION_GENERATED:
								Log.d(LOG, "might want to reexamine this logpack:\n" + captureEvent.toString());
								break;
							}
						}
					}

					c = null;
				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}				
			}
		}
		progress += 5;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		// XXX: will i need this to have been created already? create local path to save log to...
		if(rootFolder == null) {
			try {
				rootFolder = MediaHasher.hash(("log_" + String.valueOf(startTime)).getBytes(), "SHA-1");

			} catch (NoSuchAlgorithmException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		info.guardianproject.iocipher.File rootFolder_ = new info.guardianproject.iocipher.File(rootFolder);
		if(!rootFolder_.exists()) {
			rootFolder_.mkdir();
		}

		if(attachedMedia != null && attachedMedia.size() > 0) {
			int progressIncrement = (int) (50/(attachedMedia.size() * 2));

			try {
				put(Models.IMedia.ILog.ATTACHED_MEDIA, new JSONArray());
			} catch(JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			for(IMedia m : attachedMedia) {
				// exported only to iocipher! not a share!
				if(m.export(h, organization, false)) {
					progress += progressIncrement;
					sendMessage(Codes.Keys.UI.PROGRESS, progress);
				}
			}

			// push bytes to j3mZip
			try {
				JSONArray attachedMedia = getJSONArray(Models.IMedia.ILog.ATTACHED_MEDIA);
				for(int a=0; a<attachedMedia.length(); a++) {
					JSONObject versionManifest = attachedMedia.getJSONObject(a);

					byte[] versionBytes = informaCam.ioService.getBytes(versionManifest.getString("absolutePath"), Type.IOCIPHER);
					j3mZip.put(versionManifest.getString("name"), versionBytes);

					versionBytes = null;
					informaCam.ioService.delete(versionManifest.getString("absolutePath"), Type.IOCIPHER);

					progress += progressIncrement;
					sendMessage(Codes.Keys.UI.PROGRESS, progress);
				}
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		if(genealogy == null) {
			genealogy = new IGenealogy();
		}

		genealogy.createdOnDevice = informaCam.user.pgpKeyFingerprint;
		genealogy.dateCreated = this.startTime;
		genealogy.localMediaPath = rootFolder;
		progress += 5;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		if(intent == null) {
			intent = new IIntent();
		}
		intent.alias = informaCam.user.alias;
		intent.pgpKeyFingerprint = informaCam.user.pgpKeyFingerprint;
		progress += 5;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		notification.label = informaCam.a.getString(R.string.export);

		notification.content = informaCam.a.getString(R.string.you_exported_this_x, "log");
		if(organization != null) {
			intent.intendedDestination = organization.organizationName;
			notification.content = informaCam.a.getString(R.string.you_exported_this_x_to_x, "log", organization.organizationName);
		}
		progress += 5;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		JSONObject j3mObject = null;
		try {
			j3mObject = new JSONObject();
			j3mObject.put(Models.IMedia.j3m.DATA, data.asJson());
			j3mObject.put(Models.IMedia.j3m.GENEALOGY, genealogy.asJson());
			j3mObject.put(Models.IMedia.j3m.INTENT, intent.asJson());
			j3mObject.put(Models.IMedia.j3m.SIGNATURE, new String(informaCam.signatureService.signData(j3mObject.toString().getBytes())));			
			Log.d(LOG, "here we have a start at j3m:\n" + j3mObject.toString());

			j3mZip.put("log.j3m", j3mObject.toString().getBytes());

			// zip up everything, encrypt if required
			String logName = ("log_" + System.currentTimeMillis());
			if(share) {
				java.io.File log = new java.io.File(Storage.EXTERNAL_DIR, logName);
				IOUtility.zipFiles(j3mZip, log.getAbsolutePath(), Type.FILE_SYSTEM);

				if(organization != null) {
					byte[] j3mBytes = informaCam.ioService.getBytes(log.getAbsolutePath(), Type.FILE_SYSTEM);
					j3mBytes = EncryptionUtility.encrypt(j3mBytes, Base64.encode(informaCam.ioService.getBytes(organization.publicKeyPath, Type.IOCIPHER), Base64.DEFAULT));
					informaCam.ioService.saveBlob(j3mBytes, log, true);
				}

			} else {
				info.guardianproject.iocipher.File log = new info.guardianproject.iocipher.File(rootFolder, logName);
				IOUtility.zipFiles(j3mZip, log.getAbsolutePath(), Type.IOCIPHER);

				if(organization != null) {
					byte[] j3mBytes = informaCam.ioService.getBytes(log.getAbsolutePath(), Type.IOCIPHER);
					j3mBytes = EncryptionUtility.encrypt(j3mBytes, Base64.encode(informaCam.ioService.getBytes(organization.publicKeyPath, Type.IOCIPHER), Base64.DEFAULT));
					informaCam.ioService.saveBlob(j3mBytes, log);
				}
			}			

			progress += 5;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

			notification.generateId();
			informaCam.addNotification(notification);

		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return true;
	}

	@Override
	protected void sendMessage(String key, int what) {
		Bundle b = new Bundle();
		b.putInt(key, what);
		Message msg = new Message();
		msg.setData(b);

		proxyHandler.sendMessage(msg);
	}

	@Override
	protected void sendMessage(String key, String what) {
		Bundle b = new Bundle();
		b.putString(key, what);
		Message msg = new Message();
		msg.setData(b);

		proxyHandler.sendMessage(msg);
	}
}
