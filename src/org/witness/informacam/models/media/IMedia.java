package org.witness.informacam.models.media;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.informa.embed.ImageConstructor;
import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.json.JSONArray;
import org.witness.informacam.json.JSONException;
import org.witness.informacam.json.JSONObject;
import org.witness.informacam.json.JSONTokener;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IData;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntakeData;
import org.witness.informacam.models.j3m.IIntent;
import org.witness.informacam.models.j3m.IRegionData;
import org.witness.informacam.models.j3m.ISensorCapture;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.TransportUtility;
import org.witness.informacam.ui.editors.IRegionDisplay;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Models.IUser;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;
import org.witness.informacam.utils.Constants.Suckers.Geo;
import org.witness.informacam.utils.MediaHasher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

public class IMedia extends Model implements MetadataEmbededListener {
	public String rootFolder = null;
	
	public String _id = null;
	public String alias = null;
		
	public long lastEdited = 0L;
	public boolean isNew = false;
	public List<String> associatedCaches = null;
	public List<IRegion> associatedRegions = null;
	public int width = 0;
	public int height = 0;

	public IDCIMEntry dcimEntry = null;
	public IIntakeData intakeData = null;
	
	public IData data = null;
	public IIntent intent = null;
	public IGenealogy genealogy = null;
	
	public CharSequence detailsAsText = null;

//	protected Handler responseHandler;
	protected boolean debugMode = false;
	
	private Bitmap mThumbnail = null;
	
	public Bitmap getBitmap(IAsset bitmapAsset) {
		return IOUtility.getBitmapFromFile(bitmapAsset.path, bitmapAsset.source);
	}

	public Bitmap testImage ()
	{
		return getThumbnail ();
	}

	public Bitmap getThumbnail ()
	{
		if (mThumbnail == null && dcimEntry.thumbnail != null)
			mThumbnail = getBitmap(dcimEntry.thumbnail);
		
		return mThumbnail;
	}
	
	public boolean delete() {
		return InformaCam.getInstance().mediaManifest.removeMediaItem(this);
	}
	
	public IForm attachForm(Activity a, IForm form) throws InstantiationException, IllegalAccessException {
		IRegion region = addRegion(a, null);
		
		form = new IForm(form, a);		
		
		return region.addForm(form);
	}
	
	public List<IForm> getForms(Activity a) throws InstantiationException, IllegalAccessException {
		IRegion region = getTopLevelRegion();
		List<IForm> forms = new ArrayList<IForm>();
		if (region != null)	{
			for(IForm form : region.associatedForms) {
				byte[] answerBytes = InformaCam.getInstance().ioService.getBytes(form.answerPath, Type.IOCIPHER);
				forms.add(new IForm(form, a, answerBytes));
			}
		}
		return forms;
	}
	
	public IForm getForm(Activity a) throws InstantiationException, IllegalAccessException {
		IRegion region = getTopLevelRegion();
		
		IForm form = region.associatedForms.get(0);
		byte[] answerBytes = InformaCam.getInstance().ioService.getBytes(form.answerPath, Type.IOCIPHER);
		
		return new IForm(form, a, answerBytes);
	}
	
	public IRegion getTopLevelRegion() throws InstantiationException, IllegalAccessException {
		return getRegionAtRect();
	}
	
	public List<IRegion> getInnerLevelRegions() {
		List<IRegion> innerLevelFormRegions = new ArrayList<IRegion>();
		if (associatedRegions != null) {
			for(IRegion region : associatedRegions) {
				if(region.isInnerLevelRegion()) {
					innerLevelFormRegions.add(region);
				}
			}
		}
		return innerLevelFormRegions;
	}

	public IRegion getRegionAtRect() throws InstantiationException, IllegalAccessException {
		return getRegionAtRect(0, 0, 0, 0, -1, false);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay) throws InstantiationException, IllegalAccessException {
		return getRegionAtRect(regionDisplay, 0);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay, long timestamp) throws InstantiationException, IllegalAccessException {
		IRegionBounds bounds = regionDisplay.bounds;
		return getRegionAtRect(bounds.displayTop, bounds.displayLeft, bounds.displayWidth, bounds.displayHeight, timestamp, false);
	}

	public IRegion getRegionAtRect(int top, int left, int width, int height, long timestamp, boolean byRealHeight) throws InstantiationException, IllegalAccessException {
		if(associatedRegions != null) {
			for(IRegion region : associatedRegions) {
				IRegionBounds bounds = null;

				if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					IVideoRegion videoRegion = new IVideoRegion(region);
					videoRegion = (IVideoRegion) region;
					bounds = videoRegion.getBoundsAtTime(timestamp);
					region = videoRegion;
				} else {
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
	
	public List<IRegion> getRegionsByNamespace(String namespace) {
		List<IRegion> regionsWithForms = new ArrayList<IRegion>();
		
		if(associatedRegions != null && associatedRegions.size() >0) {
			for(IRegion region : associatedRegions) {
				if(region.associatedForms.isEmpty()) {
					continue;
				}
				
				boolean regionHasForms = false;
				
				for(IForm form : region.associatedForms) {
					if(form.namespace.equals(namespace)) {
						regionHasForms = true;
						break;
					}
				}
				
				if(regionHasForms) {
					regionsWithForms.add(region);
				}
			}
		}
		
		return regionsWithForms;
	}

	public List<IRegion> getRegionsWithForms(List<String> omitableNamespaces) {
		List<IRegion> regionsWithForms = new ArrayList<IRegion>();

		if(associatedRegions != null && associatedRegions.size() > 0) {
			for(IRegion region : associatedRegions) {
				if(region.associatedForms.isEmpty()) {
					continue;
				}
				
				boolean regionHasForms = false;
				
				if(omitableNamespaces == null) {
					regionHasForms = true;
				} else {

					for(IForm form : region.associatedForms) {
						if(!omitableNamespaces.contains(form.namespace)) {
							regionHasForms = true;
							break;
						}
					}
				}
				
				if(regionHasForms) {
					regionsWithForms.add(region);
				}
			}
		}

		return regionsWithForms;
	}

	public void save() throws InstantiationException, IllegalAccessException {		
		InformaCam informaCam = InformaCam.getInstance();
		informaCam.mediaManifest.getById(_id).inflate(asJson());
		informaCam.saveState(informaCam.mediaManifest);
	
	}

	public boolean rename(String alias) {
		this.alias = alias;
		return true;
	}

	public IRegion addRegion(Activity activity, IRegionDisplayListener listener) throws InstantiationException, IllegalAccessException {
		try {
			return addRegion(activity, 0, 0, 0, 0, listener);
		} catch (JSONException e) {
			Logger.e(LOG, e);
		}

		return null;
	}

	public IRegion addRegion(Activity activity, int top, int left, int width, int height, IRegionDisplayListener listener) throws JSONException, InstantiationException, IllegalAccessException {
		return addRegion(activity, top, left, width, height, -1L, -1L, listener);
	}

	public IRegion addRegion(Activity activity, int top, int left, int width, int height, long startTime, long endTime, IRegionDisplayListener listener) throws JSONException, InstantiationException, IllegalAccessException {
		if(associatedRegions == null) {
			Logger.d(LOG, "initing associatedRegions");
			associatedRegions = new ArrayList<IRegion>();
		}

		IRegion region = new IRegion();

		if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
			IVideoRegion videoRegion = new IVideoRegion(region);
			region = videoRegion;
		}

		region.init(activity, new IRegionBounds(top, left, width, height, startTime, endTime), listener);

		boolean startedByUs = false;
		
		if (InformaCam.getInstance().informaService == null)
		{
			InformaCam.getInstance().startInforma();
			startedByUs = true;
			
			int numTries = 5;
			int tryIdx = 0;
					
			while (InformaCam.getInstance().informaService == null && tryIdx < numTries)
			{
				try {Thread.sleep(1000);}
				catch(Exception e){}
				
				tryIdx++;
				
			}
		}
		
		if (InformaCam.getInstance().informaService != null)
			InformaCam.getInstance().informaService.addRegion(region);
		
		if (startedByUs)
			InformaCam.getInstance().stopInforma();
		
		associatedRegions.add(region);
		if(region.isInnerLevelRegion()) {
			assignInnerLevelRegionIndexes();
		}
		
		//Logger.d(LOG, "added region " + region.asJson().toString() + "\nassociatedRegions size: " + associatedRegions.size());

		return region;
	}
	
	public void assignInnerLevelRegionIndexes() {
		int r = 0;
		for(IRegion region : associatedRegions) {
			if(region.isInnerLevelRegion()) {
				region.index = r;
				r++;
			}
		}
	}
	
	public void removeRegion(IRegion region) {
		boolean wasInnerLevelRegion = region.isInnerLevelRegion();
		
		region.delete(this);
		if(wasInnerLevelRegion) {
			assignInnerLevelRegionIndexes();
		}
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
	
	@SuppressWarnings("unused")
	protected void mungeData() throws FileNotFoundException, InstantiationException, IllegalAccessException {
		if(data == null) {
			data = new IData();
		}
		
		if(intakeData != null) {
			data.intakeData = intakeData;
		}
		
		if(associatedRegions != null) {
			for(IRegion region : associatedRegions) {
				
				IRegionData regionData = new IRegionData(new IRegion(region));
				
				for(IForm form : region.associatedForms) {
					
					if(regionData.associatedForms != null) {
						if(data.userAppendedData == null) {
							data.userAppendedData = new ArrayList<IRegionData>();
						}
						
						data.userAppendedData.add(regionData);
					}
										
				}
			}
		}
	}
	
	protected void mungeSensorLogs() {
		mungeSensorLogs(null);
	}

	public String getSimpleLocationString ()
	{
		
		if (data == null || data.sensorCapture == null)
		{
			mungeSensorLogs ();
		}
		
		for (ISensorCapture sc : data.sensorCapture)
		{
			if (sc.sensorPlayback.has(Geo.Keys.GPS_COORDS))
			{
				try
				{
					return sc.sensorPlayback.getString(Geo.Keys.GPS_COORDS);
				}
				catch (Exception e){}
			}
		}
	
		
		return null;
	}
	
	protected void mungeSensorLogs(Handler h) {
		if(data == null) {
			data = new IData();
		}
		
		if(associatedCaches != null && associatedCaches.size() > 0) {
			
			synchronized(associatedCaches)
			{
			
				int progress = 0;
				int progressInterval = (int) (40/associatedCaches.size());
				
				InformaCam informaCam = InformaCam.getInstance();
				data.sensorCapture = new ArrayList<ISensorCapture>();
	
				for(String ac : associatedCaches) {
					try {
						// get the data and loop through capture types
						byte[] c = informaCam.ioService.getBytes(ac, Type.IOCIPHER);
						
						if (c == null)
						{
							Log.d(LOG,"cache was null: " + ac);
							continue;
						}
						JSONArray cache = ((JSONObject) new JSONTokener(new String(c)).nextValue()).getJSONArray(Models.LogCache.CACHE);
	
						for(int i=0; i<cache.length(); i++) {
							JSONObject entry = cache.getJSONObject(i);
							long ts = Long.parseLong((String) entry.keys().next());
	
							JSONObject captureEvent = entry.getJSONObject(String.valueOf(ts));
							
							if(captureEvent.has(CaptureEvent.Keys.TYPE)) {
								JSONArray captureTypes = captureEvent.getJSONArray(CaptureEvent.Keys.TYPE);
	
								for(int ct=0; ct<captureTypes.length(); ct++) {
									switch((Integer) captureTypes.get(ct)) {
									case CaptureEvent.SENSOR_PLAYBACK:
										data.sensorCapture.add(new ISensorCapture(ts, captureEvent));							
										break;
									case CaptureEvent.REGION_GENERATED:
										Logger.d(LOG, "might want to reexamine this logpack:\n" + captureEvent.toString());
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
						Logger.e(LOG, e);
					}				
				}
			}
		}
	}

	public IAsset export(Context context, Handler h) throws FileNotFoundException, InstantiationException, IllegalAccessException {
		return export(context, h, null, true, true, false);
	}

	public IAsset export(Context context, Handler h, IOrganization organization) throws FileNotFoundException, InstantiationException, IllegalAccessException {
		return export(context, h, organization, true, false, true);
	}

	public IAsset export(Context context, Handler h, IOrganization organization, boolean includeSensorLogs, boolean isLocalShare, boolean doSubmission) throws FileNotFoundException, InstantiationException, IllegalAccessException {
		
		//Logger.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
	//	Logger.d(LOG, "ORIGINAL ASSET SETTINGS: " + dcimEntry.fileAsset.asJson().toString());
		System.gc();
		
		int progress = 0;
		InformaCam informaCam = InformaCam.getInstance();

		INotification notification = new INotification();
		notification.icon = dcimEntry.thumbnail;

		// create data package
		if(data == null) {
			data = new IData();
		}
		data.exif = dcimEntry.exif;
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		if (includeSensorLogs)
			mungeSensorLogs();
		
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		mungeData();
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
			
			IAsset j3mAsset = addAsset(Models.IMedia.Assets.J3M);
			
			if(!debugMode) {
				
				OutputStream os = null;
				
				if (j3mAsset.source == Type.FILE_SYSTEM)
					os = new java.io.FileOutputStream(j3mAsset.path);
				else if (j3mAsset.source == Type.IOCIPHER)
					os = new info.guardianproject.iocipher.FileOutputStream(j3mAsset.path);
				
				ByteArrayInputStream is = new ByteArrayInputStream(j3mObject.toString().getBytes());
				
				// encrypt if the organization is not null
				if(organization != null)
				{					
					EncryptionUtility.encrypt(is, os, Base64.encode(informaCam.ioService.getBytes(organization.publicKey, Type.IOCIPHER), Base64.DEFAULT));				
				}
				else
				{				
					IOUtils.copyLarge(is, os);
				}	
				
				os.flush();
				os.close();
			}

			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

			String exportFileName = System.currentTimeMillis() + "_" + this.dcimEntry.name;
			notification.generateId();
			notification.mediaId = this._id;

			ITransportStub submission = null;
			int exportDestination = Type.IOCIPHER;
			if(isLocalShare) {
				exportDestination = Type.FILE_SYSTEM;
				notification.type = Models.INotification.Type.SHARED_MEDIA;
			} else {

				if(!(Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
					exportDestination = Type.FILE_SYSTEM;
				}
				notification.type = Models.INotification.Type.EXPORTED_MEDIA;
				
				if(organization != null && doSubmission) {
					notification.taskComplete = false;
					informaCam.addNotification(notification, h);
					submission = new ITransportStub(organization, notification);
				}
			}
			
			IAsset exportAsset = new IAsset(exportDestination);
			exportAsset.name = exportFileName;
			exportAsset.path = IOUtility.buildPath(new String[] { rootFolder, exportFileName });
			
			if(exportDestination == Type.FILE_SYSTEM) {
				exportAsset.path = IOUtility.buildPublicPath(new String[] { exportAsset.path });
			}
			
			if(this.dcimEntry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
				exportAsset.name = exportAsset.name.replace(".mp4", ".mkv");
				exportAsset.path = exportAsset.path.replace(".mp4", ".mkv");
			}
			
			//Logger.d(LOG, "EXPORT ASSET SETTINGS: " + exportAsset.asJson().toString());
			constructExport(exportAsset, submission);
			
			if(submission != null) {
				submission.setAsset(exportAsset, dcimEntry.mediaType, exportDestination);
			}
			
			informaCam.addNotification(notification, h);			
			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

			return exportAsset;
			
		} catch (JSONException e) {
			Logger.e(LOG, e);
			
		} catch (ConcurrentModificationException e) {
			Logger.e(LOG, e);
			
		} catch (Exception e) {
			Logger.e(LOG, e);
			
		}
		
		return null;
	}
	
	private void constructExport(IAsset destinationAsset, ITransportStub submission) throws IOException {
		if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
			@SuppressWarnings("unused")
			ImageConstructor ic = new ImageConstructor(this, destinationAsset, submission);
		} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
			@SuppressWarnings("unused")
			VideoConstructor vc = new VideoConstructor(InformaCam.getInstance(), this, destinationAsset, submission);
		}
	}
	
	public String exportHash() {
		
		//generate public hash id from values
		String creatorHash = genealogy.createdOnDevice;
		StringBuffer mediaHash = new StringBuffer();
		for(String mHash : genealogy.hashes) {
			mediaHash.append(mHash);
		}
				
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
			md.update((creatorHash + mediaHash.toString()).getBytes());
			byte[] byteData = md.digest();

			StringBuffer hexString = new StringBuffer();
			for (int i=0;i<byteData.length;i++) {
				String hex=Integer.toHexString(0xff & byteData[i]);
				if(hex.length()==1) hexString.append('0');
				hexString.append(hex);
			}

			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}

	public boolean exportJ3M(Context context, Handler h, IOrganization organization, boolean share) throws FileNotFoundException, InstantiationException, IllegalAccessException {
	//	Logger.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
	//	Logger.d(LOG, "ORIGINAL ASSET SETTINGS: " + dcimEntry.fileAsset.asJson().toString());
		System.gc();
		
		int progress = 0;
		InformaCam informaCam = InformaCam.getInstance();

		INotification notification = new INotification();
		notification.icon = dcimEntry.thumbnail;

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

		mungeData();
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
			
			IAsset j3mAsset = addAsset(Models.IMedia.Assets.J3M);
			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

			notification.generateId();
			notification.mediaId = this._id;
			
			if(!debugMode) {
				
				OutputStream os = null;
				
				if (j3mAsset.source == Type.FILE_SYSTEM)
					os = new java.io.FileOutputStream(j3mAsset.path);
				else if (j3mAsset.source == Type.IOCIPHER)
					os = new info.guardianproject.iocipher.FileOutputStream(j3mAsset.path);
				
				//os = new Base64OutputStream(new GZIPOutputStream(os), Base64.DEFAULT);
				ByteArrayInputStream is = new ByteArrayInputStream(j3mObject.toString().getBytes());
				
				// encrypt if the organization is not null
				if(organization != null)
				{					
					EncryptionUtility.encrypt(is, os, Base64.encode(informaCam.ioService.getBytes(organization.publicKey, Type.IOCIPHER), Base64.DEFAULT));				
				}
				else
				{				
					IOUtils.copyLarge(is, os);
				}	
				
				os.flush();
				os.close();
				
			}
			
			ITransportStub submission = null;
			if(share) {
				notification.type = Models.INotification.Type.SHARED_MEDIA;
			} else {
				notification.type = Models.INotification.Type.EXPORTED_MEDIA;
				
				if(organization != null) {
					notification.taskComplete = false;
					informaCam.addNotification(notification, h);
					
					submission = new ITransportStub(organization, notification);
					submission.setAsset(j3mAsset, dcimEntry.mediaType, Storage.Type.IOCIPHER);
				}
			}
			
			onMetadataEmbeded(j3mAsset);
			progress += 10;
			sendMessage(Codes.Keys.UI.PROGRESS, progress);

		} catch (JSONException e) {
			Logger.e(LOG, e);
			return false;
		} catch (ConcurrentModificationException e) {
			Logger.e(LOG, e);
			return false;
		}
		catch (Exception e) {
			Logger.e(LOG, e);
			return false;
		}

		return true;
	}

	public String buildJ3M(Context context, boolean signData) throws FileNotFoundException, InstantiationException, IllegalAccessException {
		
		Logger.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
	//	Logger.d(LOG, "ORIGINAL ASSET SETTINGS: " + dcimEntry.fileAsset.asJson().toString());
		System.gc();
		
		int progress = 0;
		InformaCam informaCam = InformaCam.getInstance();

		INotification notification = new INotification();
		notification.icon = dcimEntry.thumbnail;

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

		mungeData();
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		mungeGenealogyAndIntent();
		progress += 20;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		/*
		String mimeType = dcimEntry.mediaType.equals(MimeType.IMAGE) ? context.getString(R.string.image) :context.getString(R.string.video);
		if(dcimEntry.mediaType.equals(MimeType.LOG)) {
			mimeType = context.getString(R.string.log);
		}
		*/
		
		progress += 10;
		sendMessage(Codes.Keys.UI.PROGRESS, progress);

		JSONObject j3mObject = null;
		try {
			j3mObject = new JSONObject();
			JSONObject j3m = new JSONObject();
			
			j3m.put(Models.IMedia.j3m.DATA, data.asJson());
			j3m.put(Models.IMedia.j3m.GENEALOGY, genealogy.asJson());
			j3m.put(Models.IMedia.j3m.INTENT, intent.asJson());
			
			if (signData)
			{
				byte[] sig = informaCam.signatureService.signData(j3m.toString().getBytes());			
				j3mObject.put(Models.IMedia.j3m.SIGNATURE, new String(sig));
			}
			
			j3mObject.put(Models.IMedia.j3m.J3M, j3m);

			return j3mObject.toString();
			
		} catch (JSONException e) {
			Logger.e(LOG, e);
			
		} catch (Exception e) {
			Logger.e(LOG, e);
			
		}

		return null;
	}
	
	public String renderDetailsAsText(int depth) {
		StringBuffer details = new StringBuffer();
		switch(depth) {
		case 1:
			if(this.alias != null) {
				details.append(this.alias);
			}
			details.append(this._id);
			//Logger.d(LOG, this.asJson().toString());

			break;
		}

		return details.toString();
	}

	public boolean analyze() throws IOException {
		isNew = true;
		
		if(genealogy == null) {
			genealogy = new IGenealogy();
		}

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
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
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

		if(h != null) {
			h.sendMessage(msg);
		}
	}

	protected void sendMessage(String key, int what, Handler h) {
		Bundle b = new Bundle();
		b.putInt(key, what);
		Message msg = new Message();
		msg.setData(b);

		if(h != null) {
			h.sendMessage(msg);
		}
	}
	
	protected void reset() throws InstantiationException, IllegalAccessException {
		data.userAppendedData = null;
		data.sensorCapture = null;
		
		save();
	}
	
	@Override
	public void onMediaReadyForTransport(final ITransportStub transportStub) {
		if(!transportStub.organization.keyReceived) {
			InformaCam informaCam = InformaCam.getInstance();
			
			INotification notification = new INotification(informaCam.getString(R.string.key_sent), informaCam.getString(R.string.you_have_sent_your_credentials_to_x, transportStub.organization.organizationName), Models.INotification.Type.NEW_KEY);
			notification.taskComplete = false;
			informaCam.addNotification(notification, null);
			
			ITransportStub credentialStub = new ITransportStub(transportStub.organization, notification);
			credentialStub.setAsset(Models.IUser.PUBLIC_CREDENTIALS, Models.IUser.PUBLIC_CREDENTIALS, MimeType.ZIP, Type.IOCIPHER);
			credentialStub.callbackCode = Models.ITransportStub.CallbackCodes.UPDATE_ORGANIZATION_HAS_KEY;
			
			TransportUtility.initTransport(credentialStub);
		}
		
		TransportUtility.initTransport(transportStub);
		
	}
	
	@Override
	public void onMetadataEmbeded(IAsset version) {
		try
		{
			reset();
			sendMessage(Models.IMedia.VERSION, version.path);
		}
		catch (Exception e)
		{
			Logger.d(LOG,"unable to process IAsset: " + version.name);
			Logger.e(LOG,e);
		}
	}
	
	public IAsset addAsset(String name) {
		String path = IOUtility.buildPath(new String[] { rootFolder, Models.IMedia.Assets.J3M });
		int source = dcimEntry.fileAsset.source;
		
		if(source == Type.FILE_SYSTEM) {
			path = IOUtility.buildPublicPath(new String[] { path });
		}
		
		return new IAsset(path, source, name);
	}

	public IAsset getAsset(String name) {
		InformaCam informaCam = InformaCam.getInstance();
		String path = IOUtility.buildPath(new String[] { rootFolder, name });
		int source = dcimEntry.fileAsset.source;
		
		if(source == Type.FILE_SYSTEM) {
			path = IOUtility.buildPublicPath(new String[] { path });
		}
		
		if(informaCam.ioService.getBytes(path, source) == null) {
			return null;
		}
		
		return new IAsset(path, source, name);
	}	
}