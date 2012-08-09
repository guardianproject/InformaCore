package org.witness.informacam.informa;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.crypto.SignatureUtility;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Storage.Tables;

import com.google.common.cache.LoadingCache;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class Informa {
	Intent intent;
	Genealogy genealogy;
	Data data;
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	Context c;
	SharedPreferences sp;
	
	public interface InformaListener {
		public void onInformaInit();
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
		long dateCreated;
		EditLog editLog;
	}
	
	public class Data extends InformaZipper {
		Exif exif;
		MediaHash mediaHash;
		Set<MediaCapturePlayback> mediaCapturePlayback;
		Set<Annotation> annotations;
		
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
	
	public class EditLog extends InformaZipper {
		long dateAccessed;
		Device device;
		Set<String> performedEdits;
		
		public EditLog() {
			
		}
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
		String unredactedHash, redactedHash, editIndex;
		
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
		String editIndex, obfuscationType;
		Location location;
		Subject subject;
		RegionBounds regionBounds;
		
		public Annotation(long timestamp, LogPack logPack) {
			this.timestamp = timestamp;
			Log.d(App.LOG, "ADDING AN ANNOTATION:\n" + logPack.toString());
		}
	}
	
	public class Subject extends InformaZipper {
		int[] customCodes;
		String alias;
		
		public Subject() {}
	}
	
	public class RegionBounds extends InformaZipper {
		JSONObject regionDimensions, regionCoordinates;
		
		public RegionBounds() {
			regionDimensions = new JSONObject();
			regionCoordinates = new JSONObject();
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
				new String[] {PGP.Keys.PGP_KEY_ID, PGP.Keys.PGP_KEY_ID});
		if(cursor != null && cursor.moveToFirst()) {
			intent.owner = new Owner();
						
			intent.owner.publicKeyFingerprint = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_FINGERPRINT));
			intent.owner.publicKeyEmail = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS));
			intent.owner.ownershipType = Constants.Informa.Owner.INDIVIDUAL;	// TODO: FOR NOW...
			
			cursor.close();
		}
	}
	
	public void setInitialData(Entry<Long, LogPack> initialData) throws JSONException {
		data.exif = new Exif(initialData.getValue());
		genealogy.dateCreated = initialData.getKey();
		data.mediaHash.unredactedHash = initialData.getValue().getString(Constants.Informa.Keys.Data.Description.ORIGINAL_HASH);
	}
	
	public void addToPlayback(List<Entry<Long, LogPack>> playback) throws JSONException {
		for(Entry<Long, LogPack> e : playback) {
			if(SignatureUtility.getInstance().isVerified(e.getValue())) {
				e.getValue().remove(CaptureEvent.Keys.TYPE);
				data.mediaCapturePlayback.add(new MediaCapturePlayback(e.getKey(), e.getValue()));
			}
		}
	}
	
	public void addToAnnotations(List<Entry<Long, LogPack>> annotations) throws JSONException {
		for(Entry<Long, LogPack> e : annotations) {
			if(SignatureUtility.getInstance().isVerified(e.getValue())) {
				e.getValue().remove(CaptureEvent.Keys.TYPE);
				data.annotations.add(new Annotation(e.getKey(), e.getValue()));
			}
		}
	}
	
	public void setDeviceCredentials(LogPack initPack) throws JSONException {
		genealogy.createdOnDevice = new Device();
		
		genealogy.createdOnDevice.imei = initPack.getString(Suckers.Phone.Keys.IMEI);
		genealogy.createdOnDevice.bluetoothAddress = initPack.getString(Suckers.Phone.Keys.BLUETOOTH_DEVICE_ADDRESS);
		genealogy.createdOnDevice.bluetoothName = initPack.getString(Suckers.Phone.Keys.BLUETOOTH_DEVICE_NAME);
		genealogy.createdOnDevice.deviceFingerprint = intent.owner.publicKeyFingerprint;
	}
	
	public Informa(Context c) {
		this.c = c;
		sp = PreferenceManager.getDefaultSharedPreferences(this.c);
		
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(sp.getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		intent = new Intent();
		genealogy = new Genealogy();
		data = new Data();
		
		getOwnerCredentials();
	}
}
