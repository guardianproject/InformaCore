package org.witness.informacam.models.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.informa.embed.ImageConstructor;
import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.models.IDCIMEntry;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.models.connections.IMessage;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.j3m.IData;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntent;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.ui.IRegionDisplay;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.MediaHasher;

import android.graphics.Bitmap;
import android.graphics.Rect;
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

	public IDCIMEntry dcimEntry = null;

	public IData data = null;
	public IIntent intent = null;
	public IGenealogy genealogy = null;
	public List<IMessage> messages = null;

	public CharSequence detailsAsText = null;
	
	private String currentJ3MDescriptorHash = null;

	public Bitmap getBitmap(String pathToFile) {
		return IOUtility.getBitmapFromFile(pathToFile, Type.IOCIPHER);
	}

	public boolean delete() {
		InformaCam informaCam = InformaCam.getInstance();

		if(informaCam.mediaManifest.media.remove(this)) {
			informaCam.ioService.delete(rootFolder, Type.IOCIPHER);
			informaCam.mediaManifest.save();
			return true;
		}

		return false;
	}

	public IRegion getRegionAtRect() {
		return getRegionAtRect(0, 0, dcimEntry.exif.width, dcimEntry.exif.height, -1L, true);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay) {
		return getRegionAtRect(regionDisplay, -1L);
	}

	public IRegion getRegionAtRect(IRegionDisplay regionDisplay, long timestamp) {
		IRegionBounds bounds = regionDisplay.bounds;
		return getRegionAtRect(bounds.displayTop, bounds.displayLeft, bounds.displayWidth, bounds.displayHeight, timestamp, false);
	}

	public IRegion getRegionAtRect(int top, int left, int width, int height, long timestamp, boolean byRealHeight) {
		for(IRegion region : associatedRegions) {
			IRegionBounds bounds = null;

			if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
				bounds = ((IImageRegion) region).bounds;
			} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
				bounds = ((IVideoRegion) region).getBoundsAtTime(timestamp);
			}

			if(byRealHeight) {
				if(bounds != null && bounds.top == top && bounds.left == left && bounds.width == width && bounds.height == height) {
					if(region instanceof IImageRegion) {
						return region;
					} else if(region instanceof IVideoRegion && (bounds.startTime <= timestamp && timestamp <= bounds.endTime)) {
						return region;
					}
				}
			} else {
				if(bounds != null && bounds.displayTop == top && bounds.displayLeft == left && bounds.displayWidth == width && bounds.displayHeight == height) {
					if(region instanceof IImageRegion) {
						return region;
					} else if(region instanceof IVideoRegion && (bounds.startTime <= timestamp && timestamp <= bounds.endTime)) {
						return region;
					}
				}
			}

		}

		return null;
	}

	public List<IRegion> getRegionsWithForms() {
		List<IRegion> regionsWithForms = null;

		if(associatedRegions != null && associatedRegions.size() > 0) {
			for(IRegion region : associatedRegions) {
				if(region.formNamespace != null) {
					if(regionsWithForms == null) {
						regionsWithForms = new ArrayList<IRegion>();
					}

					regionsWithForms.add(region);
				}
			}
		}

		return regionsWithForms;
	}

	public boolean rename(String alias) {
		this.alias = alias;
		return true;
	}

	public IRegion addRegion() {
		IRegion region = null;
		if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
			try {
				region = addRegion(0, 0, dcimEntry.exif.width, dcimEntry.exif.height);
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

		} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
			try {
				region = addRegion(0, 0, dcimEntry.exif.width, dcimEntry.exif.height, 0);
				((IVideoRegion) region).trail.get(0).endTime = dcimEntry.exif.duration;
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		return region;
	}

	public IRegion addRegion(int top, int left, int width, int height) throws JSONException {
		return addRegion(top, left, width, height, -1L);
	}

	public IRegion addRegion(int top, int left, int width, int height, long startTime) throws JSONException {
		if(associatedRegions == null) {
			Log.d(LOG, "initing associatedRegions");
			associatedRegions = new ArrayList<IRegion>();
		}

		IRegion region;
		if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
			region = new IImageRegion();
		} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
			region = new IVideoRegion();
		} else {
			region = new IRegion();
		}

		region.init(new IRegionBounds(top, left, width, height, startTime));
		associatedRegions.add(region);
		Log.d(LOG, "added region " + region.asJson().toString() + "\nassociatedRegions size: " + associatedRegions.size());

		return region;
	}

	public void removeRegion(IRegion region) {
		region.delete(this);
	}

	public boolean export() {
		return export(null, true);
	}
	
	public boolean export(IOrganization organization) {
		return export(organization, false);
	}

	public boolean export(IOrganization organization, boolean share) {
		Log.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
		InformaCam informaCam = InformaCam.getInstance();

		// create data package
		if(data == null) {
			data = new IData();
		}
		data.exif = dcimEntry.exif;
		if(associatedCaches != null && associatedCaches.size() > 0) { 
			for(String ac : associatedCaches) {
				// TODO: render these to data.sensorCapture 

			}
		}

		if(genealogy == null) {
			genealogy = new IGenealogy();
		}
		genealogy.createdOnDevice = informaCam.user.pgpKeyFingerprint;
		genealogy.dateCreated = dcimEntry.timeCaptured;
		genealogy.localMediaPath = dcimEntry.fileName;

		if(intent == null) {
			intent = new IIntent();
		}
		intent.alias = informaCam.user.alias;
		intent.pgpKeyFingerprint = informaCam.user.pgpKeyFingerprint;

		if(organization != null) {
			intent.intendedDestination = organization.organizationName;
		}

		JSONObject j3mObject = null;
		try {
			j3mObject = new JSONObject();
			j3mObject.put(Models.IMedia.j3m.DATA, data.asJson());
			j3mObject.put(Models.IMedia.j3m.GENEALOGY, genealogy.asJson());
			j3mObject.put(Models.IMedia.j3m.INTENT, intent.asJson());
			j3mObject.put(Models.IMedia.j3m.SIGNATURE, new String(informaCam.signatureService.signData(j3mObject.toString().getBytes())));			
			Log.d(LOG, "here we have a start at j3m:\n" + j3mObject.toString());
			
			
			// base64
			byte[] j3mBytes = Base64.encode(j3mObject.toString().getBytes(), Base64.DEFAULT);

			info.guardianproject.iocipher.File j3mFile = new info.guardianproject.iocipher.File(rootFolder, this.dcimEntry.originalHash + "_" + System.currentTimeMillis() + ".j3m");
			if(organization != null) {
				j3mBytes = EncryptionUtility.encrypt(j3mBytes, informaCam.ioService.getBytes(organization.publicKeyPath, Type.IOCIPHER));
			}
			
			byte[] j3mZip = IOUtility.zipBytes(j3mBytes, j3mFile.getName(), Type.IOCIPHER);
			informaCam.ioService.saveBlob(j3mZip, j3mFile);

			String exportFileName = System.currentTimeMillis() + "_" + this.dcimEntry.name;
			info.guardianproject.iocipher.File original = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
			if(share) {
				// create a java.io.file
				java.io.File shareFile = new java.io.File(Storage.EXTERNAL_DIR, exportFileName);
				if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
					
					ImageConstructor imageConstructor = new ImageConstructor(this, original, j3mFile, shareFile.getAbsolutePath(), Type.FILE_SYSTEM);
				} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					VideoConstructor videoConstructor = new VideoConstructor(this, original, j3mFile, shareFile.getAbsolutePath(), Type.FILE_SYSTEM);
				}
			} else {
				// create a java.io.file
				info.guardianproject.iocipher.File exportFile = new info.guardianproject.iocipher.File(rootFolder, exportFileName);

				if(organization != null){
					// create connection and send to queue or export as file
					ISubmission submission = new ISubmission(organization, exportFile.getAbsolutePath());
					submission.isHeld = true;
					informaCam.uploaderService.addToQueue(submission);
				}
				
				if(dcimEntry.mediaType.equals(MimeType.IMAGE)) {
					ImageConstructor imageConstructor = new ImageConstructor(this, original, j3mFile, exportFile.getAbsolutePath(), Type.IOCIPHER);
				} else if(dcimEntry.mediaType.equals(MimeType.VIDEO)) {
					VideoConstructor videoConstructor = new VideoConstructor(this, original, j3mFile, exportFile.getAbsolutePath(), Type.IOCIPHER);
				}
			}
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
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

	public void analyze() {
		isNew = true;

		try {
			info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(dcimEntry.originalHash);
			this.rootFolder = rootFolder.getAbsolutePath();

			if(!rootFolder.exists()) {
				rootFolder.mkdir();
			}
		} catch (ExceptionInInitializerError e) {}

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

	@Override
	public void onMetadataEmbeded(info.guardianproject.iocipher.File version) {
		InformaCam informaCam = InformaCam.getInstance();
		IPendingConnections pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());
		if(pendingConnections.queue != null) {
			for(IConnection connection : pendingConnections.queue) {
				if(connection instanceof ISubmission && ((ISubmission) connection).pathToNextConnectionData.equals(version.getAbsolutePath())) {
					((ISubmission) connection).Set(version);
					connection.isHeld = false;
					informaCam.saveState(pendingConnections);
					
					break;
				}
			}
		}

	}

	@Override
	public void onMetadataEmbeded(File version) {
		// TODO Auto-generated method stub
		
	}	
}
