package org.witness.informacam.informa;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

public class Informa {
	Intent intent;
	Genealogy genealogy;
	Data data;
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	
	public interface InformaListener {
		public void onInformaInit(Activity editor, Uri originalUri);
	}
	
	private class InformaZipper extends JSONObject {
		Field[] fields;
		
		public InformaZipper() {
			fields = this.getClass().getDeclaredFields();
		}
		
		public JSONObject zip() throws IllegalArgumentException, IllegalAccessException, JSONException {
			for(Field f : fields) {
				f.setAccessible(true);
				Object value = f.get(this);
				
				if(!(value instanceof Informa)) {	//  && !(value instanceof Image)) {
					if(value instanceof InformaZipper)
						this.put(f.getName(), ((InformaZipper) value).zip());
					else if(value instanceof Set) {
						Iterator<?> i = ((Set<?>) value).iterator();
						JSONArray j = new JSONArray();
						while(i.hasNext())
							j.put(((InformaZipper) i.next()).zip());
						this.put(f.getName(), j);
					} else
						this.put(f.getName(), value);
				}
			}
			
			return this;
		}
	}
	
	public class Intent extends InformaZipper {
		Owner owner;
		String intendedDestination;
		
		public Intent() {
			
		}
	}
	
	public class Genealogy extends InformaZipper {
		String localMediaPath;
		Device createdOnDevice;
		long dateCreated, dateSavedAsInformaDocument;
	}
	
	public class Data extends InformaZipper {
		Exif exif;
		MediaHash mediaHash;
		Set<MediaCapturePlayback> mediaCapturePlayback;
		Set<Annotation> annotations;
		int mediaType;
		
		public Data() {
			mediaCapturePlayback = new HashSet<MediaCapturePlayback>();
			annotations = new HashSet<Annotation>();
			mediaHash = new MediaHash();
		}
	}
	
	public class Owner extends InformaZipper {
		String publicKeyFingerprint, publicKeyEmail;
		int ownershipType;
		
		public Owner() {}
	}
	
	public class Device extends InformaZipper {
		String imei, bluetoothAddress, bluetoothName, deviceFingerprint;
		
		public Device() {}
		
	}
	
	public class Exif extends InformaZipper {
		int sdk, orientation, imageLength, imageWidth, whiteBalance, flash, focalLength;
		String make, model, iso, exposureTime, aperture, editIndex;
		
		public Exif(LogPack exif) throws JSONException {
			this.sdk = Build.VERSION.SDK_INT;
			if(exif.has(Constants.Informa.Keys.Data.Exif.MAKE))
				this.make = exif.getString(Constants.Informa.Keys.Data.Exif.MAKE);
			if(exif.has(Constants.Informa.Keys.Data.Exif.MODEL))
				this.model = exif.getString(Constants.Informa.Keys.Data.Exif.MODEL);
			if(exif.has(Constants.Informa.Keys.Data.Exif.ORIENTATION))
				this.orientation = exif.getInt(Constants.Informa.Keys.Data.Exif.ORIENTATION);
			if(exif.has(Constants.Informa.Keys.Data.Exif.IMAGE_LENGTH))
				this.imageLength = exif.getInt(Constants.Informa.Keys.Data.Exif.IMAGE_LENGTH);
			if(exif.has(Constants.Informa.Keys.Data.Exif.IMAGE_WIDTH))
				this.imageWidth = exif.getInt(Constants.Informa.Keys.Data.Exif.IMAGE_WIDTH);
			if(exif.has(Constants.Informa.Keys.Data.Exif.ISO))
				this.iso = exif.getString(Constants.Informa.Keys.Data.Exif.ISO);
			if(exif.has(Constants.Informa.Keys.Data.Exif.WHITE_BALANCE))
				this.whiteBalance = exif.getInt(Constants.Informa.Keys.Data.Exif.WHITE_BALANCE);
			if(exif.has(Constants.Informa.Keys.Data.Exif.FLASH))
				this.flash = exif.getInt(Constants.Informa.Keys.Data.Exif.FLASH);
			if(exif.has(Constants.Informa.Keys.Data.Exif.EXPOSURE))
				this.exposureTime = exif.getString(Constants.Informa.Keys.Data.Exif.EXPOSURE);
			if(exif.has(Constants.Informa.Keys.Data.Exif.FOCAL_LENGTH))
				this.focalLength = exif.getInt(Constants.Informa.Keys.Data.Exif.FOCAL_LENGTH);
			if(exif.has(Constants.Informa.Keys.Data.Exif.APERTURE))
				this.aperture = exif.getString(Constants.Informa.Keys.Data.Exif.APERTURE);
		}
	}
	
	public class MediaHash extends InformaZipper {
		String unredactedHash, redactedHash;
		
		public MediaHash() {}
	}
	
	public class MediaCapturePlayback extends InformaZipper {
		long timestamp;
		String editIndex;
		LogPack sensorPlayback;
		
		public MediaCapturePlayback(long timestamp, LogPack sensorPlayback) {
			this.timestamp = timestamp;
			this.sensorPlayback = sensorPlayback;
		}
	}
	
	public class Annotation extends InformaZipper {
		long timestamp;
		String obfuscationType;
		Location location;
		Subject subject;
		RegionBounds regionBounds;
		
		public Annotation(long timestamp, LogPack logPack) {
			this.timestamp = timestamp;
			try {
				obfuscationType = logPack.getString(ImageRegion.FILTER);
				regionBounds = new RegionBounds(
						logPack.getString(ImageRegion.WIDTH),
						logPack.getString(ImageRegion.HEIGHT),
						logPack.getString(ImageRegion.COORDINATES));
				
				if(logPack.has(ImageRegion.Subject.PSEUDONYM)) {
					subject = new Subject(
							logPack.getString(ImageRegion.Subject.PSEUDONYM), 
							logPack.getString(ImageRegion.Subject.INFORMED_CONSENT_GIVEN));
				}
			} catch(JSONException e){}
		}
	}
	
	public class Subject extends InformaZipper {
		int[] customCodes;
		String alias;
		
		public Subject(String alias, String consentGiven) {
			this.alias = alias;
			if(new Boolean(consentGiven)) {
				this.customCodes = new int[1];
				this.customCodes[0] = Constants.Informa.Consent.GENERAL;
			}
		}
	}
	
	public class RegionBounds extends InformaZipper {
		JSONObject regionDimensions, regionCoordinates;
		
		public RegionBounds(String regionWidth, String regionHeight, String regionCoordinates) {
			regionDimensions = new JSONObject();
			this.regionCoordinates = new JSONObject();
			
			try {
				regionDimensions.put(ImageRegion.HEIGHT, Float.parseFloat(regionHeight));
				regionDimensions.put(ImageRegion.WIDTH, Float.parseFloat(regionWidth));
				
				this.regionCoordinates.put(ImageRegion.LEFT, Float.parseFloat(regionCoordinates.substring(1, regionCoordinates.indexOf(","))));
				this.regionCoordinates.put(ImageRegion.TOP, Float.parseFloat(regionCoordinates.substring(regionCoordinates.indexOf(",") + 1, regionCoordinates.length() - 1)));
				
			} catch(JSONException e){}
		}
	}
	
	public class Location extends InformaZipper {
		long cellId;
		float[] geoCoordinates;
		
		public Location() {}
	}
	
	private void getOwnerCredentials() {
		dh.setTable(db, Tables.Keys.KEYRING);
		Cursor cursor = dh.getJoinedValue(
				db, 
				new String[] {PGP.Keys.PGP_FINGERPRINT, PGP.Keys.PGP_EMAIL_ADDRESS}, 
				new String[] {Tables.Keys.KEYRING, Tables.Keys.SETUP},
				new String[] {PGP.Keys.PGP_KEY_ID, PGP.Keys.PGP_KEY_ID},
				null);
		if(cursor != null && cursor.moveToFirst()) {
			intent.owner = new Owner();
						
			intent.owner.publicKeyFingerprint = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_FINGERPRINT));
			intent.owner.publicKeyEmail = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS));
			intent.owner.ownershipType = Constants.Informa.Owner.INDIVIDUAL;	// TODO: FOR NOW...
			
			cursor.close();
		}
	}
	
	public String getPgpKeyFingerprint() {
		return intent.owner.publicKeyFingerprint;
	}
	
	public ContentValues initMetadata(String versionPath, long trustedDestinationId) throws NoSuchAlgorithmException, IOException {
		ContentValues cv = new ContentValues();
		
		cv.put(Media.Keys.TYPE, data.mediaType);
		cv.put(Media.Keys.ORIGINAL_HASH, data.mediaHash.unredactedHash);
		cv.put(Media.Keys.ANNOTATED_HASH, MediaHasher.hash(bundle().toString().getBytes(), "SHA-1"));
		cv.put(Media.Keys.TIME_CAPTURED, genealogy.dateCreated);
		cv.put(Media.Keys.LOCATION_OF_ORIGINAL, genealogy.localMediaPath);
		cv.put(Media.Keys.LOCATION_OF_SENT, versionPath);
		cv.put(Media.Keys.TRUSTED_DESTINATION_ID, trustedDestinationId);
		cv.put(Media.Keys.STATUS, Media.Status.IDLE);
		
		return cv;
	}
	
	public boolean setInitialData(Entry<Long, LogPack> initialData) throws JSONException {
		data.exif = new Exif(initialData.getValue());
		genealogy.dateCreated = initialData.getKey();
		data.mediaHash.unredactedHash = initialData.getValue().getString(Constants.Informa.Keys.Data.Description.ORIGINAL_HASH);
		data.mediaType = initialData.getValue().getInt(Constants.Informa.Keys.Data.Description.MEDIA_TYPE);
		return true;
	}
	
	public void setFileInformation(String localMediaPath) {
		genealogy.localMediaPath = localMediaPath;
	}
	
	public boolean addToPlayback(List<Entry<Long, LogPack>> playback) throws JSONException {
		for(Entry<Long, LogPack> e : playback) {
			e.getValue().remove(CaptureEvent.Keys.TYPE);
			data.mediaCapturePlayback.add(new MediaCapturePlayback(e.getKey(), e.getValue()));
		}
		return true;
	}
	
	public boolean addToAnnotations(List<Entry<Long, LogPack>> annotations) throws JSONException {
		for(Entry<Long, LogPack> e : annotations) {
			e.getValue().remove(CaptureEvent.Keys.TYPE);
			data.annotations.add(new Annotation(e.getKey(), e.getValue()));
		}
		return true;
	}
	
	public void setDeviceCredentials(LogPack initPack) throws JSONException {
		genealogy.createdOnDevice = new Device();
		
		genealogy.createdOnDevice.imei = initPack.getString(Suckers.Phone.Keys.IMEI);
		genealogy.createdOnDevice.bluetoothAddress = initPack.getString(Suckers.Phone.Keys.BLUETOOTH_DEVICE_ADDRESS);
		genealogy.createdOnDevice.bluetoothName = initPack.getString(Suckers.Phone.Keys.BLUETOOTH_DEVICE_NAME);
		genealogy.createdOnDevice.deviceFingerprint = intent.owner.publicKeyFingerprint;
	}
	
	public void setTrustedDestination(String email) {
		intent.intendedDestination = email;
	}
	
	public void setSaveTime(long saveTime) {
		genealogy.dateSavedAsInformaDocument = saveTime;
	}
	
	public Informa() {		
		dh = DatabaseService.getInstance().getHelper();
		db = DatabaseService.getInstance().getDb();
		
		intent = new Intent();
		genealogy = new Genealogy();
		data = new Data();
		
		getOwnerCredentials();
	}

	public String bundle() {
		JSONObject bundle = new JSONObject();
		try {
			bundle.put(Constants.Informa.Keys.INTENT, intent.zip());
			bundle.put(Constants.Informa.Keys.GENEALOGY, genealogy.zip());
			bundle.put(Constants.Informa.Keys.DATA, data.zip());
			
			return bundle.toString();
		} catch(JSONException e) {
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		}
		
	}
}
