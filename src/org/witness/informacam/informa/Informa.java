package org.witness.informacam.informa;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.utils.Constants;

import android.os.Build;

public class Informa {
	
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
		
		public Data() {
			
		}
	}
	
	public class Owner extends InformaZipper {
		String publicKeyFingerprint, publicKeyEmail;
		int ownershipType;
		
		public Owner() {
			
		}
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
		
		public MediaHash() {
			
		}
	}
	
	public class MediaCapturePlayback extends InformaZipper {
		long timestamp;
		String editIndex;
		LogPack sensorPlayback;
		
		public MediaCapturePlayback() {
			
		}
	}
	
	public Informa() {
		// should init intent with owner, genealogy
	}
}
