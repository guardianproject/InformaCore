package org.witness.informacam.models.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.informa.embed.ImageConstructor;
import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IData;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntent;
import org.witness.informacam.models.j3m.IRegionData;
import org.witness.informacam.models.j3m.ISensorCapture;
import org.witness.informacam.models.notifications.IMail;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.models.utils.IRegionDisplay;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.DriveTransport;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.TransportUtility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

public class IMedia extends Model implements MetadataEmbededListener {
	
	public String rootFolder = null;
	public String _id = null;
	public String _rev = null;
	public String alias = null;
	public String bitmapThumb = null;
	public String bitmapList = null;
	public String bitmapPreview = null;
	public long lastEdited = 0L;
	public boolean isNew = false;
	public List<String> associatedCaches = null;
	public List<IRegion> associatedRegions = null;
	public int width = 0;
	public int height = 0;

	public IDCIMEntry dcimEntry = null;

	public IData data = null;
	public IIntent intent = null;
	public IGenealogy genealogy = null;
	public List<IMail> messages = null;

	public CharSequence detailsAsText = null;

	protected Handler responseHandler;
	protected boolean debugMode = false;
	
	private Bitmap mThumbnail = null;
	
	public Bitmap getBitmap(String pathToFile) {
		return IOUtility.getBitmapFromFile(pathToFile, Type.IOCIPHER);
	}

	public Bitmap testImage ()
	{
		return getThumbnail ();
	}

	public Bitmap getThumbnail ()
	{
		if (mThumbnail == null && bitmapThumb != null)
			mThumbnail = getBitmap(bitmapThumb);
		
		return mThumbnail;
	}
	
	public boolean delete() {
		return InformaCam.getInstance().mediaManifest.removeMediaItem(this);
	}

	public IRegion getRegionAtRect() {
		return getRegionAtRect(0, 0, 0, 0, 0, false);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay) {
		return getRegionAtRect(regionDisplay, 0);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay, long timestamp) {
		IRegionBounds bounds = regionDisplay.bounds;
		return getRegionAtRect(bounds.displayTop, bounds.displayLeft, bounds.displayWidth, bounds.displayHeight, timestamp, false);
	}

	public IRegion getRegionAtRect(int top, int left, int width, int height, long timestamp, boolean byRealHeight) {
		if(associatedRegions != null) {
			for(IRegion region : associatedRegions) {
				IRegionBounds bounds = null;

				if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					IVideoRegion videoRegion = new IVideoRegion(region);
					videoRegion = (IVideoRegion) region;
					bounds = videoRegion.getBoundsAtTime(timestamp);
					region = videoRegion;
				} else if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
					bounds = region.bounds;
				}

				if(byRealHeight) {
					if(bounds != null && bounds.top == top && bounds.left == left && bounds.width == width && bounds.height == height) {
						return region;
					}
				} else {
					if(bounds != null && bounds.displayTop == top && bounds.displayLeft == left && bounds.displayWidth == width && bounds.displayHeight == height) {
						return region;
					}
				}

			}
		}

		return null;
	}

	public List<IRegion> getRegionsWithForms(List<String> omitableNamespaces) {
		List<IRegion> regionsWithForms = new ArrayList<IRegion>();

		if(associatedRegions != null && associatedRegions.size() > 0) {
			for(IRegion region : associatedRegions) {
				if(region.formNamespace != null) {
					if(omitableNamespaces != null && !omitableNamespaces.contains(region.formNamespace)) {
						regionsWithForms.add(region);
					}
				}
			}
		}

		return regionsWithForms;
	}

	public void save() {		
		InformaCam informaCam = InformaCam.getInstance();
		informaCam.mediaManifest.getById(_id).inflate(asJson());

		informaCam.saveState(informaCam.mediaManifest);
	}

	public boolean rename(String alias) {
		this.alias = alias;
		return true;
	}

	public IRegion addRegion(Activity activity, IRegionDisplayListener listener) {
		try {
			return addRegion(activity, 0, 0, 0, 0, listener);
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return null;
	}

	public IRegion addRegion(Activity activity, int top, int left, int width, int height, IRegionDisplayListener listener) throws JSONException {
		return addRegion(activity, top, left, width, height, -1L, -1L, listener);
	}

	public IRegion addRegion(Activity activity, int top, int left, int width, int height, long startTime, long endTime, IRegionDisplayListener listener) throws JSONException {
		if(associatedRegions == null) {
			Log.d(LOG, "initing associatedRegions");
			associatedRegions = new ArrayList<IRegion>();
		}

		IRegion region = new IRegion();

		if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
			IVideoRegion videoRegion = new IVideoRegion(region);
			region = videoRegion;
		}

		region.init(activity, new IRegionBounds(top, left, width, height, startTime, endTime), listener);

		associatedRegions.add(region);
		Log.d(LOG, "added region " + region.asJson().toString() + "\nassociatedRegions size: " + associatedRegions.size());

		return region;
	}

	public void removeRegion(IRegion region) {
		region.delete(this);
	}

	protected void mungeGenealogyAndIntent() {
		InformaCam informaCam = InformaCam.getInstance();

		if(genealogy == null) {
			genealogy = new IGenealogy();
		}

		genealogy.createdOnDevice = informaCam.user.pgpKeyFingerprint;
		genealogy.localMediaPath = rootFolder;

		if(intent == null) {
			intent = new IIntent();
		}
		intent.alias = informaCam.user.alias;
		intent.pgpKeyFingerprint = informaCam.user.pgpKeyFingerprint;
	}
	
	protected void mungeSensorLogs() {
		mungeSensorLogs(null);
	}

	protected void mungeSensorLogs(Handler h) {
		if(data == null) {
			data = new IData();
		}
		
		if(associatedCaches != null && associatedCaches.size() > 0) {
			int progress = 0;
			int progressInterval = (int) (40/associatedCaches.size());
			
			InformaCam informaCam = InformaCam.getInstance();
			data.sensorCapture = new ArrayList<ISensorCapture>();

			for(String ac : associatedCaches) {
				try {
					// get the data and loop through capture types
					byte[] c = informaCam.ioService.getBytes(ac, Type.IOCIPHER);
					JSONArray cache = ((JSONObject) new JSONTokener(new String(c)).nextValue()).getJSONArray(Models.LogCache.CACHE);

					for(int i=0; i<cache.length(); i++) {
						JSONObject entry = cache.getJSONObject(i);
						long ts = Long.parseLong((String) entry.keys().next());

						JSONObject captureEvent = entry.getJSONObject(String.valueOf(ts));
						
						if(captureEvent.has(CaptureEvent.Keys.TYPE)) {
							// TODO: this should be better formulated.
							JSONArray captureTypes = captureEvent.getJSONArray(CaptureEvent.Keys.TYPE);

							for(int ct=0; ct<captureTypes.length(); ct++) {
								switch((Integer) captureTypes.get(ct)) {
								case CaptureEvent.SENSOR_PLAYBACK:
									data.sensorCapture.add(new ISensorCapture(ts, captureEvent));							
									break;
								case CaptureEvent.REGION_GENERATED:
									Log.d(LOG, "might want to reexamine this logpack:\n" + captureEvent.toString());
									break;
								}
							}
						} else {
							data.sensorCapture.add(new ISensorCapture(ts, captureEvent));
						}
					}

					c = null;
					
					progress += progressInterval;
					sendMessage(Codes.Keys.UI.PROGRESS, progress, h);
				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}				
			}
		}
	}

	public boolean export(Context context, Handler h) {
		return export(context, h, null, true);
	}

	public boolean export(Context context, Handler h, IOrganization organization) {
		return export(context, h, organization, false);
	}

	@SuppressWarnings("unused")
	public boolean export(Context context, Handler h, IOrganization organization, boolean share) {
		Log.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
		System.gc();
		
		responseHandler = h;
		int progress = 0;
		InformaCam informaCam = InformaCam.getInstance();

		INotification notification = new INotification();
		notification.icon = bitmapThumb;

		// create data package
		if(data == null) {
			data = new IData();
		}
		data.exif = dcimEntry.exif;
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		mungeSensorLogs();
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		if(associatedRegions != null && associatedRegions.size() > 0) {
			for(IRegion r : associatedRegions) {
				if(r.formPath != null) {
					if(data.regionData == null) {
						data.regionData = new ArrayList<IRegionData>();
					}

					// TODO: get locations from cache
					byte[] formBytes = informaCam.ioService.getBytes(r.formPath, Type.IOCIPHER);
					if(formBytes != null && formBytes.length > 0) {
						data.regionData.add(new IRegionData(r, IOUtility.xmlToJson(new ByteArrayInputStream(formBytes))));
					}
				}
			}
		}
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		mungeGenealogyAndIntent();
		progress += 20;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		notification.label = context.getString(R.string.export);

		String mimeType = dcimEntry.mediaType.equals(MimeType.IMAGE) ? context.getString(R.string.image) :context.getString(R.string.video);
		if(dcimEntry.mediaType.equals(MimeType.LOG)) {
			mimeType = context.getString(R.string.log);
		}

		notification.content = context.getString(R.string.you_exported_this_x, mimeType);
		if(organization != null) {
			intent.intendedDestination = organization.organizationName;
			notification.content = context.getString(R.string.you_exported_this_x_to_x, mimeType, organization.organizationName);
		}
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		JSONObject j3mObject = null;
		try {
			j3mObject = new JSONObject();
			JSONObject j3m = new JSONObject();
			
			j3m.put(Models.IMedia.j3m.DATA, data.asJson());
			j3m.put(Models.IMedia.j3m.GENEALOGY, genealogy.asJson());
			j3m.put(Models.IMedia.j3m.INTENT, intent.asJson());
			
			byte[] sig = informaCam.signatureService.signData(j3m.toString().getBytes());
			
			j3mObject.put(Models.IMedia.j3m.SIGNATURE, new String(sig));
			
			j3mObject.put(Models.IMedia.j3m.J3M, j3m);
			Log.d(LOG, "here we have a start at j3m:\n" + j3mObject.toString());
			
			info.guardianproject.iocipher.File j3mFile = new info.guardianproject.iocipher.File(rootFolder, this.dcimEntry.originalHash + "_" + System.currentTimeMillis() + ".j3m");

			byte[] j3mBytes = j3mObject.toString().getBytes();
			
			if(!debugMode) {
				// zip *FIRST
				j3mBytes = IOUtility.zipBytes(j3mBytes, j3mFile.getName(), Type.IOCIPHER);

				// maybe encrypt
				if(organization != null) {
					j3mBytes = EncryptionUtility.encrypt(j3mBytes, Base64.encode(informaCam.ioService.getBytes(organization.publicKey, Type.IOCIPHER), Base64.DEFAULT));
				}
				
				// base64
				j3mBytes = Base64.encode(j3mBytes, Base64.DEFAULT);
				
			}

			informaCam.ioService.saveBlob(j3mBytes, j3mFile);
			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

			String exportFileName = System.currentTimeMillis() + "_" + this.dcimEntry.name;
			info.guardianproject.iocipher.File original = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);

			notification.generateId();
			if(share) {
				// create a java.io.file
				java.io.File shareFile = new java.io.File(Storage.EXTERNAL_DIR, exportFileName);
				if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
					ImageConstructor imageConstructor = new ImageConstructor(this, original, j3mFile, shareFile.getAbsolutePath(), Type.FILE_SYSTEM);
				} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					VideoConstructor videoConstructor = new VideoConstructor(context, this, original, j3mFile, shareFile.getAbsolutePath().replace(".mp4", ".mkv"), Type.FILE_SYSTEM);
				}

				notification.type = Models.INotification.Type.SHARED_MEDIA;				
				informaCam.addNotification(notification);
			} else {
				// create a iocipher file
				notification.type = Models.INotification.Type.EXPORTED_MEDIA;
				
				info.guardianproject.iocipher.File exportFile = new info.guardianproject.iocipher.File(rootFolder, exportFileName);
				ITransportStub submission = null; 

				if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					if(organization != null) {
						notification.taskComplete = false;
						informaCam.addNotification(notification);
						
						submission = new ITransportStub(exportFile.getAbsolutePath().replace(".mp4", ".mkv"), organization, notification);
						submission.mimeType = Models.IMedia.MimeType.VIDEO;
						submission.assetName = exportFile.getName().replace(".mp4", ".mkv");
					}

					VideoConstructor videoConstructor = new VideoConstructor(context, this, original, j3mFile, exportFile.getAbsolutePath().replace(".mp4", ".mkv"), Type.IOCIPHER, submission);
				} else if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
					if(organization != null) {
						notification.taskComplete = false;
						informaCam.addNotification(notification);
						
						submission = new ITransportStub(exportFile.getAbsolutePath(), organization, notification);
						submission.mimeType = Models.IMedia.MimeType.IMAGE;
						submission.assetName = exportFile.getName();
					}

					ImageConstructor imageConstructor = new ImageConstructor(this, original, j3mFile, exportFile.getAbsolutePath(), Type.IOCIPHER, submission);
				}
			}
			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

		} catch (JSONException e) {
			Log.e(LOG, e.toString(),e);
			return false;
		} catch (ConcurrentModificationException e) {
			Log.e(LOG, e.toString(),e);
			return false;
		}
		catch (IOException e) {
			Log.e(LOG, e.toString(),e);
			return false;
		}

		return true;
	}

	public String renderDetailsAsText(int depth) {
		StringBuffer details = new StringBuffer();
		switch(depth) {
		case 1:
			if(this.alias != null) {
				details.append(this.alias);
			}
			details.append(this._id);
			Log.d(LOG, this.asJson().toString());

			break;
		}

		return details.toString();
	}

	public boolean analyze() {
		isNew = true;

		try {
			info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(dcimEntry.originalHash);
			this.rootFolder = rootFolder.getAbsolutePath();

			if(!rootFolder.exists()) {
				rootFolder.mkdir();
			}
			
			return true;
		} catch (ExceptionInInitializerError e) {}
		
		return false;

	}

	public String generateId(String seed) {
		try {
			return MediaHasher.hash(KeyUtility.generatePassword(seed.getBytes()).getBytes(), "MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return seed;
	}
	
	protected void sendMessage(String key, String what) {
		sendMessage(key, what, null);
	}
	
	protected void sendMessage(String key, int what) {
		sendMessage(key, what, null);
	}

	protected void sendMessage(String key, String what, Handler h) {
		Bundle b = new Bundle();
		b.putString(key, what);
		Message msg = new Message();
		msg.setData(b);

		if(h == null) {
			responseHandler.sendMessage(msg);
		} else {
			h.sendMessage(msg);
		}
	}

	protected void sendMessage(String key, int what, Handler h) {
		Bundle b = new Bundle();
		b.putInt(key, what);
		Message msg = new Message();
		msg.setData(b);

		if(h == null) {
			responseHandler.sendMessage(msg);
		} else {
			h.sendMessage(msg);
		}
	}
	
	@Override
	public void onMetadataEmbeded(ITransportStub transportStub) {
		sendMessage(Models.IMedia.VERSION, transportStub.assetPath);
		TransportUtility.initTransport(transportStub);
	}

	@Override
	public void onMetadataEmbeded(info.guardianproject.iocipher.File version) {
		sendMessage(Models.IMedia.VERSION, version.getAbsolutePath());
	}

	@Override
	public void onMetadataEmbeded(java.io.File version) {
		sendMessage(Models.IMedia.VERSION, version.getAbsolutePath());
	}	
}
