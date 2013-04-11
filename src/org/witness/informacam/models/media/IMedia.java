package org.witness.informacam.models.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.IDCIMEntry;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.connections.IMessage;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.j3m.IData;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntent;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.MediaHasher;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

public class IMedia extends Model implements MetadataEmbededListener {
	public String rootFolder = null;
	public String _id, _rev, alias = null;
	public String bitmapThumb, bitmapList, bitmapPreview = null;
	public long lastEdited = 0L;
	public boolean isNew = false;
	public List<String> associatedCaches = null;
	public List<String> associatedForms = null;
	public List<IRegion> associatedRegions = null;

	public IDCIMEntry dcimEntry = null;

	public IData data = null;
	public IIntent intent = null;
	public IGenealogy genealogy = null;
	public List<IMessage> messages = null;

	public CharSequence detailsAsText = null;

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

	public boolean rename(String alias) {
		this.alias = alias;
		return true;
	}

	public boolean export() {
		return export(null, true);
	}

	public boolean export(IOrganization organization, boolean share) {
		Log.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
		InformaCam informaCam = InformaCam.getInstance();

		// create data package
		if(data == null) {
			data = new IData();
		}
		data.originalData = dcimEntry.exif;
		if(associatedCaches != null && associatedCaches.size() > 0) { 
			for(String ac : associatedCaches) {
				// TODO: render these to data.sensorCapture 

			}
		}

		JSONObject j3mObject = null;
		try {
			j3mObject = new JSONObject();
			j3mObject.put(Models.IMedia.j3m.DATA, data.asJson());
			j3mObject.put(Models.IMedia.j3m.GENEALOGY, genealogy.asJson());
			j3mObject.put(Models.IMedia.j3m.INTENT, intent.asJson());
			j3mObject.put(Models.IMedia.j3m.SIGNATURE, Base64.encode(informaCam.signatureService.signData(j3mObject.toString().getBytes()), Base64.DEFAULT));

			// base64
			byte[] j3mBytes = Base64.encode(j3mObject.toString().getBytes(), Base64.DEFAULT);

			// zip
			info.guardianproject.iocipher.File j3mFile = new info.guardianproject.iocipher.File(rootFolder, "j3m_" + System.currentTimeMillis());
			byte[] j3mZip = IOUtility.zipBytes(j3mBytes, new info.guardianproject.iocipher.FileInputStream(j3mFile), Type.IOCIPHER);

			// encrypt
			byte[] j3m = j3mZip;
			if(organization != null) {
				j3m = EncryptionUtility.encrypt(j3mZip, informaCam.ioService.getBytes(organization.publicKeyPath, Type.IOCIPHER));
			}

			if(share) {
				// create a java.io.file
				java.io.File shareFile = new java.io.File(Storage.EXTERNAL_DIR, j3mFile.getName());
				return embed(shareFile, j3mFile, null);
			} else {
				// create a java.io.file
				info.guardianproject.iocipher.File exportFile = new info.guardianproject.iocipher.File(rootFolder, "export_" + System.currentTimeMillis());
				
				if(organization != null){
					// create connection and send to queue or export as file
					ISubmission submission = new ISubmission(organization);
					submission.isHeld = true;
					return embed(exportFile, j3mFile, submission);
				} else {
					return embed(exportFile, j3mFile, null);
				}
			}
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}


		return false;
	}

	protected boolean embed(info.guardianproject.iocipher.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
		return false;
	}
	protected boolean embed(java.io.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
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
	public void onMetadataEmbeded(File version) {
		// TODO Auto-generated method stub
		
	}	
}
