package org.witness.informacam.app.editors.image;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.app.editors.image.filters.CrowdPixelizeObscure;
import org.witness.informacam.app.editors.image.filters.InformaTagger;
import org.witness.informacam.app.editors.image.filters.PixelizeObscure;
import org.witness.informacam.app.editors.image.filters.SolidObscure;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.App.ImageEditor;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Storage.Tables;

import com.google.common.cache.LoadingCache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Base64;
import android.util.Log;

public class ImageConstructor {
	static {
		System.loadLibrary("JpegRedaction");
	}
	
	private DatabaseHelper dh;
	private SQLiteDatabase db;
	
	public static native int constructImage(
			String originalImageFilename, 
			String informaImageFilename, 
			String metadataObjectString, 
			int metadataLength);
	
	public static native byte[] redactRegion(
			String originalImageFilename,
			String informaImageFilename,
			int left,
			int right,
			int top,
			int bottom,
			String redactionCommand);
	
	public ImageConstructor(Context c, LoadingCache<Long, LogPack> annotationCache, long[] encryptList) {						
		try {
			// XXX: create clone as flat file -- should not need to do this though :(
			Uri originalUri = InformaService.getInstance().originalUri;
			java.io.File clone = IOCipherService.getInstance().moveFromIOCipherToMemory(originalUri, originalUri.getLastPathSegment()); 
						
			// get all image regions and run through image constructor
			List<Entry<Long, LogPack>> annotations = InformaService.getInstance().getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED, annotationCache);
			
			for(Entry<Long, LogPack> entry : annotations) {
				LogPack lp = entry.getValue();
				Log.d(Storage.LOG, lp.toString());
				
				String redactionMethod = lp.getString(Constants.Informa.Keys.Data.ImageRegion.FILTER);
				if(!redactionMethod.equals(InformaTagger.class.getName())) {
					String redactionCode = "";
					
					if(redactionMethod.equals(PixelizeObscure.class.getName()))
						redactionCode = ImageEditor.Filters.PIXELIZE;
					else if(redactionMethod.equals(SolidObscure.class.getName()))
						redactionCode = ImageEditor.Filters.SOLID;
					else if(redactionMethod.equals(CrowdPixelizeObscure.class.getName()))
						redactionCode = ImageEditor.Filters.CROWD_PIXELIZE;
					
					String regionCoordinates = lp.getString(Constants.Informa.Keys.Data.ImageRegion.COORDINATES);
					
					int left = (int) Float.parseFloat(regionCoordinates.substring(regionCoordinates.indexOf(",") + 1, regionCoordinates.length() - 1));
					int top = (int) Float.parseFloat(regionCoordinates.substring(1, regionCoordinates.indexOf(",")));
					int right = (int) (left + Float.parseFloat(lp.getString(Constants.Informa.Keys.Data.ImageRegion.WIDTH)));
					int bottom = (int) (top + Float.parseFloat(lp.getString(Constants.Informa.Keys.Data.ImageRegion.HEIGHT)));
					
					Log.d(App.LOG, "top: " + top + " left: " + left + " right " + right + " bottom " + bottom + " redaction " + redactionCode);
					
					byte[] redactionPack = redactRegion(clone.getAbsolutePath(), clone.getAbsolutePath(), left, right, top, bottom, redactionCode);
										
					JSONObject imageRegionData = new JSONObject();
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.LENGTH, redactionPack.length);
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.HASH, MediaHasher.hash(redactionPack, "SHA-1"));
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.BYTES, Base64.encode(redactionPack, Base64.DEFAULT));
					
					lp.put(Constants.Informa.Keys.Data.ImageRegion.UNREDACTED_DATA, imageRegionData);
				}
			}
			
			if(InformaService.getInstance().informa.addToAnnotations(annotations)) {
				// then it is ok... time to encrypt
				dh = new DatabaseHelper(c);
				db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(c).getString(Settings.Keys.CURRENT_LOGIN, ""));
				
				long saveTime = System.currentTimeMillis();
				InformaService.getInstance().informa.setSaveTime(saveTime);
				
				dh.setTable(db, Tables.Keys.KEYRING);
				
				// for each trusted destination
				for(long td : encryptList) {
					Log.d(Storage.LOG, "keyring id: " + td);
					Cursor cursor = dh.getValue(db, new String[] {PGP.Keys.PGP_DISPLAY_NAME, PGP.Keys.PGP_EMAIL_ADDRESS, PGP.Keys.PGP_PUBLIC_KEY, Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID}, TrustedDestination.Keys.KEYRING_ID, td);
					
					if(cursor != null && cursor.moveToFirst()) {
						for(String s : cursor.getColumnNames())
							Log.d(Storage.LOG, s);
						
						String forName = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_DISPLAY_NAME));
						Log.d(Storage.LOG, forName);
						
						// add into intent
						InformaService.getInstance().informa.setTrustedDestination(cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS)));
						
						
						// bundle up informadata
						String informaMetadata = EncryptionUtility.encrypt(InformaService.getInstance().informa.bundle().getBytes(), cursor.getBlob(cursor.getColumnIndex(PGP.Keys.PGP_PUBLIC_KEY)));
						
						
						// insert metadata
						File version = new File(Storage.FileIO.DUMP_FOLDER, System.currentTimeMillis() + "_" + forName.replace(" ", "-") + Media.Type.JPEG);
						constructImage(clone.getAbsolutePath(), version.getAbsolutePath(), informaMetadata, informaMetadata.length());
						
						// save metadata in database
						dh.setTable(db, Tables.Keys.MEDIA);
						db.insert(dh.getTable(), null, InformaService.getInstance().informa.initMetadata(version.getAbsolutePath(), cursor.getLong(cursor.getColumnIndex(Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID))));
						
						cursor.close();
						
					}
				}
				
				db.close();
				dh.close();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {}
		
		
		// add to upload queue if possible
		
		
	}
}