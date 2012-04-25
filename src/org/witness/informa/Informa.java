package org.witness.informa;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.CaptureEvents;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.CaptureEvent;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.MediaHasher;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class Informa {
	public Intent intent;
	public Genealogy genealogy;
	public Data data;
	
	private SQLiteDatabase db;
	private DatabaseHelper dh;
	private Apg apg;
	private Image[] images;
	
	private Context _c;
	
	private class InformaZipper extends JSONObject {
		Field[] fields;
		
		public InformaZipper() {
			fields = this.getClass().getDeclaredFields();
		}
		
		public JSONObject zip() throws IllegalArgumentException, IllegalAccessException, JSONException {
			for(Field f : fields) {
				f.setAccessible(true);
				Object value = f.get(this);
				
				if(!(value instanceof Informa)  && !(value instanceof Image)) {
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
		int securityLevel;
		
		public Intent(String intendedDestination) {
			this.owner = new Owner();
			this.securityLevel = (Integer) getDBValue(Keys.Tables.SETUP, new String[] {Keys.Owner.DEFAULT_SECURITY_LEVEL}, BaseColumns._ID, 1, Integer.class);
			this.intendedDestination = intendedDestination;
		}
	}
	
	public class Genealogy extends InformaZipper {
		String localMediaPath;
		long dateCreated, dateAcquired;
		
		public Genealogy() {}
	}
	
	public class Data extends InformaZipper {
		int sourceType;
		String imageHash;
		Device device;
		Exif exif;
		
		Set<CaptureTimestamp> captureTimestamp;
		Set<Location> location;
		Set<Corroboration> corroboration;
		Set<ImageRegion> imageRegions;
		Set<Event> events;
		
		public Data(int sourceType) {
			this.sourceType = sourceType;
			
			this.captureTimestamp = new HashSet<CaptureTimestamp>();
			this.location = new HashSet<Location>();
			this.corroboration = new HashSet<Corroboration>();
			if(sourceType == InformaConstants.MediaTypes.PHOTO) {
				this.imageRegions = new HashSet<ImageRegion>();
			}
		}
	}
	
	public class Exif extends InformaZipper {
		int sdk, orientation, imageLength, imageWidth, whiteBalance, flash, focalLength;
		String make, model, iso, exposureTime, aperture;
		
		public Exif(JSONObject exif) throws JSONException {
			this.sdk = Build.VERSION.SDK_INT;
			if(exif.has(Keys.Exif.MAKE))
				this.make = exif.getString(Keys.Exif.MAKE);
			if(exif.has(Keys.Exif.MODEL))
				this.model = exif.getString(Keys.Exif.MODEL);
			if(exif.has(Keys.Exif.ORIENTATION))
				this.orientation = exif.getInt(Keys.Exif.ORIENTATION);
			if(exif.has(Keys.Exif.IMAGE_LENGTH))
				this.imageLength = exif.getInt(Keys.Exif.IMAGE_LENGTH);
			if(exif.has(Keys.Exif.IMAGE_WIDTH))
				this.imageWidth = exif.getInt(Keys.Exif.IMAGE_WIDTH);
			if(exif.has(Keys.Exif.ISO))
				this.iso = exif.getString(Keys.Exif.ISO);
			if(exif.has(Keys.Exif.WHITE_BALANCE))
				this.whiteBalance = exif.getInt(Keys.Exif.WHITE_BALANCE);
			if(exif.has(Keys.Exif.FLASH))
				this.flash = exif.getInt(Keys.Exif.FLASH);
			if(exif.has(Keys.Exif.EXPOSURE))
				this.exposureTime = exif.getString(Keys.Exif.EXPOSURE);
			if(exif.has(Keys.Exif.FOCAL_LENGTH))
				this.focalLength = exif.getInt(Keys.Exif.FOCAL_LENGTH);
			if(exif.has(Keys.Exif.APERTURE))
				this.aperture = exif.getString(Keys.Exif.APERTURE);
		}
	}
	
	public class Device extends InformaZipper {
		String imei;
		Corroboration bluetoothInfo;
		
		public Device(String imei, Corroboration bluetoothInfo) {
			this.imei = imei;
			this.bluetoothInfo = bluetoothInfo;
		}
	}
	
	public class CaptureTimestamp extends InformaZipper {
		int timestampType;
		long timestamp;
		
		public CaptureTimestamp(int timestampType, long timestamp) {
			this.timestampType = timestampType;
			this.timestamp = timestamp;
		}
	}
	
	public class Location extends InformaZipper {
		int locationType;
		JSONObject locationData;
		
		public Location(int locationType, JSONObject locationData) {
			this.locationType = locationType;
			this.locationData = locationData;
		}
	}
	
	public class Corroboration extends InformaZipper {
		String deviceBTAddress, deviceBTName;
		int selfOrNeighbor;
		long timeSeen;
		
		public Corroboration(String deviceBTAddress, String deviceBTName, int selfOrNeighbor, long timeSeen) {
			this.deviceBTAddress = deviceBTAddress;
			this.deviceBTName = deviceBTName;
			this.selfOrNeighbor = selfOrNeighbor;
			this.timeSeen = timeSeen;
		}
	}
	
	public class Event extends InformaZipper {
		JSONObject eventData;
		int eventType;
		
		public Event(int eventType, JSONObject eventData) {
			this.eventType = eventType;
			this.eventData = eventData;
		}
	}
	
	public class Subject extends InformaZipper {
		String subjectName;
		boolean informedConsentGiven;
		String consentGiven;
		
		public Subject(String subjectName, boolean informedConsentGiven, String consentGiven) {
			this.subjectName = subjectName;
			this.informedConsentGiven = informedConsentGiven;
			this.consentGiven = consentGiven;
		}
	}
	
	public class ImageRegion extends InformaZipper {
		CaptureTimestamp captureTimestamp;
		Location location;
		
		public String obfuscationType, unredactedRegionHash;
		JSONObject regionDimensions, regionCoordinates;
		char[] unredactedRegionData;
		
		Subject subject;
		
		public ImageRegion(CaptureTimestamp captureTimestamp, Location location, String obfuscationType, JSONObject regionDimensions, JSONObject regionCoordinates) throws JSONException {
			this.captureTimestamp = captureTimestamp;
			this.location = location;
			this.obfuscationType = obfuscationType;
			this.regionDimensions = regionDimensions;
			this.regionCoordinates = regionCoordinates;
		}
	}
	
	public class Owner extends InformaZipper {
		String sigKeyId;
		int ownershipType;
		
		public Owner() {
			this.sigKeyId = getAPGEmail(apg.getSignatureKeyId());
			this.ownershipType = (Integer) getDBValue(Keys.Tables.SETUP, new String[] {Keys.Owner.OWNERSHIP_TYPE}, BaseColumns._ID, 1, Integer.class);
		}
	}
	
	private String getMimeType(int sourceType) {
		String mime = "";
		switch(sourceType) {
		case MediaTypes.PHOTO:
			mime = ".jpg";
			break;
		case MediaTypes.VIDEO:
			mime = ".mkv";
			break;
		}
		return mime;
	}
	
	public class Image extends File implements Serializable {
		private static final long serialVersionUID = 8189892791741740688L;
		private String intendedDestination;
		private JSONObject metadataPackage;

		public Image(String path, String intendedDestination) throws JSONException, IllegalArgumentException, IllegalAccessException {
			super(path);
			
			this.intendedDestination = intendedDestination;
			Informa.this.intent = new Intent(intendedDestination);
						
			this.metadataPackage = new JSONObject();
			this.metadataPackage.put(Keys.Informa.INTENT, Informa.this.intent.zip());
			this.metadataPackage.put(Keys.Informa.GENEALOGY, Informa.this.genealogy.zip());
			this.metadataPackage.put(Keys.Informa.DATA, Informa.this.data.zip());
		}
		
		public String getIntendedDestination() {
			return this.intendedDestination;
		}
		
		public String getMetadataPackage() {
			return this.metadataPackage.toString();
		}
		
	}
	
	private Object getDBValue(String table, String[] keys, String matchKey, Object matchValue, Class<?> expectedType) {
		dh.setTable(db, table);
		Object result = new Object();
		
		try {
			Cursor c = dh.getValue(db, keys, matchKey, matchValue);
			c.moveToFirst();
			
			while(!c.isAfterLast()) {
				for(String key : keys) {
					int index = c.getColumnIndex(key);
					if(expectedType.equals(Long.class)) {
						result = c.getLong(index);
					} else if(expectedType.equals(String.class)) {
						result = c.getString(index);
					} else if(expectedType.equals(Integer.class)) {
						result = c.getInt(index);
					}
				}
				c.moveToNext();
			}
			
			c.close();
			
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG, "cursor was nulllll",e);
		}
		
		return result;
	}
	
	private String getAPGEmail(long keyId) {
		return apg.getPublicUserId(_c, keyId);
	}
	
	public Image[] getImages() {
		return images;
	}
	
	public Informa(
			Context c,
			int mediaType,
			JSONObject imageData, 
			JSONArray regionData, 
			JSONArray capturedEvents, 
			long[] intendedDestinations) throws JSONException, IllegalArgumentException, IllegalAccessException  {
		
		
		_c = c;
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(_c);
		
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		apg = Apg.getInstance();
		apg.setSignatureKeyId((Long) getDBValue(Keys.Tables.SETUP, new String[] {Keys.Owner.SIG_KEY_ID}, BaseColumns._ID, 1, Long.class));
		
		data = new Data(mediaType);
		genealogy = new Genealogy();
		
		for(int e=0; e<capturedEvents.length(); e++) {
			JSONObject ce = (JSONObject) capturedEvents.get(e);
			
			int captureType = 0;
			try {
				captureType = (Integer) ce.remove(CaptureEvent.TYPE);
			} catch(NullPointerException npe) {
				Log.e(InformaConstants.TAG, "this has no capture type:\n" + ce.toString());
			}
			
			long timestamp = (Long) ce.remove(CaptureEvent.MATCH_TIMESTAMP);
			
			switch(captureType) {
			case CaptureEvents.BLUETOOTH_DEVICE_SEEN:
				if(data.corroboration == null)
					data.corroboration = new HashSet<Corroboration>();
				
				data.corroboration.add(new Corroboration(
						ce.getString(Keys.Device.BLUETOOTH_DEVICE_ADDRESS),
						ce.getString(Keys.Device.BLUETOOTH_DEVICE_NAME),
						InformaConstants.Device.IS_NEIGHBOR,
						timestamp
				));
				
				break;
			case CaptureEvents.MEDIA_CAPTURED:
				if(data.captureTimestamp == null)
					data.captureTimestamp = new HashSet<CaptureTimestamp>();
				
				data.captureTimestamp.add(new CaptureTimestamp(captureType,timestamp));
				
				if(data.location == null)
					data.location = new HashSet<Location>();
				data.location.add(new Location(captureType,ce));
				
				genealogy.dateCreated = timestamp;
				genealogy.localMediaPath = imageData.getString(Keys.Image.LOCAL_MEDIA_PATH);
				
				break;
			case CaptureEvents.MEDIA_SAVED:
				if(data.captureTimestamp == null)
					data.captureTimestamp = new HashSet<CaptureTimestamp>();
				
				data.captureTimestamp.add(new CaptureTimestamp(captureType,timestamp));
				
				if(data.location == null)
					data.location = new HashSet<Location>();
				data.location.add(new Location(captureType,ce));
				
				data.device = new Device(
						(String) ce.remove(Keys.Device.IMEI),
						new Corroboration(
								(String) ce.remove(Keys.Device.BLUETOOTH_DEVICE_ADDRESS),
								(String) ce.remove(Keys.Device.BLUETOOTH_DEVICE_NAME),
								InformaConstants.Device.IS_SELF,
								timestamp)
				);
				genealogy.dateAcquired = timestamp;
				break;
			case CaptureEvents.EXIF_REPORTED:
				data.exif = new Exif(ce.getJSONObject(Keys.Image.EXIF));
				break;
			case CaptureEvents.REGION_GENERATED:
				if(mediaType == MediaTypes.PHOTO) {
					for(int x=0; x< regionData.length(); x++) {
						JSONObject imageRegion = (JSONObject) regionData.get(x);
						
						long matchTimestamp;
						
						try {
							matchTimestamp = (Long) imageRegion.get(Keys.ImageRegion.TIMESTAMP);
						} catch(ClassCastException cce) {
							matchTimestamp = Long.parseLong((String) imageRegion.get(Keys.ImageRegion.TIMESTAMP));
						}
						
						if(timestamp == matchTimestamp) {
							JSONObject geo = (JSONObject) ce.remove(Keys.Suckers.GEO);
							JSONObject acc = (JSONObject) ce.remove(Keys.Suckers.ACCELEROMETER);
							JSONObject phone = (JSONObject) ce.remove(Keys.Suckers.PHONE);
							
							JSONObject locationOnGeneration = new JSONObject();
							locationOnGeneration.put(Keys.Location.COORDINATES, geo.getString(Keys.Suckers.Geo.GPS_COORDS));
							if(phone.has(Keys.Suckers.Phone.CELL_ID))
								locationOnGeneration.put(Keys.Location.CELL_ID, phone.getString(Keys.Suckers.Phone.CELL_ID));
							
							JSONObject regionDimensions = new JSONObject();
							regionDimensions.put(Keys.ImageRegion.WIDTH, Float.parseFloat(imageRegion.getString(Keys.ImageRegion.WIDTH)));
							regionDimensions.put(Keys.ImageRegion.HEIGHT, Float.parseFloat(imageRegion.getString(Keys.ImageRegion.HEIGHT)));
							
							String[] rCoords = imageRegion.getString(Keys.ImageRegion.COORDINATES).substring(1, imageRegion.getString(Keys.ImageRegion.COORDINATES).length() -1).split(",");
							JSONObject regionCoordinates = new JSONObject();
							regionCoordinates.put(Keys.ImageRegion.TOP, Float.parseFloat(rCoords[0]));
							regionCoordinates.put(Keys.ImageRegion.LEFT, Float.parseFloat(rCoords[1]));
							
							ImageRegion ir = new ImageRegion(
									new CaptureTimestamp(captureType, timestamp),
									new Location(captureType, locationOnGeneration),
									imageRegion.getString(Keys.ImageRegion.FILTER),
									regionDimensions,
									regionCoordinates
							);
							
							if(imageRegion.has(Keys.ImageRegion.Subject.PSEUDONYM)) {
								ir.subject = new Subject(
									imageRegion.getString(Keys.ImageRegion.Subject.PSEUDONYM),
									Boolean.parseBoolean(imageRegion.getString(Keys.ImageRegion.Subject.INFORMED_CONSENT_GIVEN)),
									"[" + InformaConstants.Consent.GENERAL + "]");
							} else
								ir.subject = null;
							
							if(data.imageRegions == null)
								data.imageRegions = new HashSet<ImageRegion>();
							
							data.imageRegions.add(ir);
							
						}
					}
				}
				break;
			}
		}
		
		try {
			data.imageHash = MediaHasher.hash(new File(genealogy.localMediaPath), "SHA-1");
		} catch (NoSuchAlgorithmException e) {}
		catch (IOException e) {}
				
		try {
			images = new Image[intendedDestinations.length];
			for(int i=0; i<intendedDestinations.length; i++) {
				dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
				try {
					Cursor td = dh.getValue(
							db, 
							new String[] {TrustedDestinations.DISPLAY_NAME, TrustedDestinations.EMAIL},
							TrustedDestinations.KEYRING_ID,
							intendedDestinations[i]);
					td.moveToFirst();
					String displayName = td.getString(td.getColumnIndex(TrustedDestinations.DISPLAY_NAME));
					String email = td.getString(td.getColumnIndex(TrustedDestinations.EMAIL));
					String newPath = 
							InformaConstants.DUMP_FOLDER + 
							genealogy.localMediaPath.substring(genealogy.localMediaPath.lastIndexOf("/"), genealogy.localMediaPath.length() - 4) +
							"_" + displayName.replace(" ", "-") +
							getMimeType(data.sourceType);
					td.close();
					images[i] = new Image(newPath, email);
				} catch(NullPointerException e) {
					Log.e(InformaConstants.TAG, "fracking npe",e);
				}
			}
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG, "there are no intended destinations",e);
		}
		
		
		db.close();
		dh.close();
	}
}
