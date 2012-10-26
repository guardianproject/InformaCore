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
import org.witness.informacam.app.editors.filters.CrowdPixelizeObscure;
import org.witness.informacam.app.editors.filters.InformaTagger;
import org.witness.informacam.app.editors.filters.PixelizeObscure;
import org.witness.informacam.app.editors.filters.SolidObscure;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.j3m.J3M;
import org.witness.informacam.j3m.J3M.J3MPackage;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.transport.UploaderService;
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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

public class ImageConstructor {
	static {
		System.loadLibrary("JpegRedaction");
	}
	
	private DatabaseHelper dh;
	private SQLiteDatabase db;
	private java.io.File clone;
	
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
	
	public interface ImageConstructorListener {
		public void onExportVersionCreated(java.io.File clone);
	}
	
	public ImageConstructor(Activity a, LoadingCache<Long, LogPack> annotationCache, String clonePath) {
		// creates the same for export only
		try {
			clone = new File(clonePath);
		
			List<Entry<Long, LogPack>> annotations = InformaService.getInstance().getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED, annotationCache);
			runRedaction(annotations);
		
			if(InformaService.getInstance().informa.addToAnnotations(annotations)) {
				long saveTime = System.currentTimeMillis();
				InformaService.getInstance().informa.setSaveTime(saveTime);
				
				String informaMetadata = InformaService.getInstance().informa.bundle();
				constructImage(clone.getAbsolutePath(), clone.getAbsolutePath(), informaMetadata, informaMetadata.length());
				
				InformaService.getInstance().versionsCreated();
				((ImageConstructorListener) a).onExportVersionCreated(clone);
			}
		} catch(JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (InterruptedException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (ExecutionException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public ImageConstructor(Context c, LoadingCache<Long, LogPack> annotationCache, long[] encryptList, String originalImagePath) {						
		try {
			// XXX: create clone as flat file -- should not need to do this though :(
			clone = new File(originalImagePath); 
						
			// get all image regions and run through image constructor
			List<Entry<Long, LogPack>> annotations = InformaService.getInstance().getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED, annotationCache);
			runRedaction(annotations);
			
			if(InformaService.getInstance().informa.addToAnnotations(annotations)) {
				// then it is ok... time to encrypt
				dh = DatabaseService.getInstance().getHelper();
				db = DatabaseService.getInstance().getDb();
				
				long saveTime = System.currentTimeMillis();
				InformaService.getInstance().informa.setSaveTime(saveTime);
				
				dh.setTable(db, Tables.Keys.KEYRING);
				
				// for each trusted destination
				for(long td : encryptList) {
					Cursor cursor = dh.getValue(db, null, TrustedDestination.Keys.KEYRING_ID, td);
					
					if(cursor != null && cursor.moveToFirst()) {
						
						String forName = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_DISPLAY_NAME));
						
						// add into intent
						InformaService.getInstance().informa.setTrustedDestination(cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS)));
						
						
						// bundle up informadata
						// TODO: encrypt if user wishes
						String informaMetadata = null;
						if(PreferenceManager.getDefaultSharedPreferences(c).getBoolean(Settings.Keys.USE_ENCRYPTION, false)) {
							byte[] key = cursor.getBlob(cursor.getColumnIndex(PGP.Keys.PGP_PUBLIC_KEY));
							informaMetadata = EncryptionUtility.encrypt(InformaService.getInstance().informa.bundle().getBytes(), key);
						} else
							informaMetadata = InformaService.getInstance().informa.bundle();
						
						
						// insert metadata
						File version = new File(Storage.FileIO.DUMP_FOLDER, System.currentTimeMillis() + "_" + forName.replace(" ", "-") + Media.Type.JPEG);
						constructImage(clone.getAbsolutePath(), version.getAbsolutePath(), informaMetadata, informaMetadata.length());
						
						// save metadata in database
						ContentValues cv = InformaService.getInstance().informa.initMetadata(MediaHasher.hash(version, "SHA-1") + "/" + version.getName(), cursor.getLong(cursor.getColumnIndex(Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID)));
						
						J3M j3m = new J3M(InformaService.getInstance().informa.getPgpKeyFingerprint(), cv, version);
						cv.put(Media.Keys.J3M_BASE, j3m.getBase());
						
						dh.setTable(db, Tables.Keys.MEDIA);
						db.insert(dh.getTable(), null, cv);
						
						// TODO: upload ticket!
						UploaderService.getInstance().requestTicket(new J3MPackage(j3m, cursor.getString(cursor.getColumnIndex(TrustedDestination.Keys.URL)), td, forName));
						
						cursor.close();
					}
				}
				
				InformaService.getInstance().versionsCreated();
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
	}

	private void runRedaction(List<Entry<Long, LogPack>> annotations) {
		for(Entry<Long, LogPack> entry : annotations) {
			LogPack lp = entry.getValue();
			try {
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
										
					byte[] redactionPack = redactRegion(clone.getAbsolutePath(), clone.getAbsolutePath(), left, right, top, bottom, redactionCode);
										
					JSONObject imageRegionData = new JSONObject();
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.LENGTH, redactionPack.length);
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.HASH, MediaHasher.hash(redactionPack, "SHA-1"));
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.BYTES, Base64.encode(redactionPack, Base64.DEFAULT));
					
					lp.put(Constants.Informa.Keys.Data.ImageRegion.UNREDACTED_DATA, imageRegionData);
				}
			} catch(JSONException e) {
				continue;
			} catch (NoSuchAlgorithmException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
		}
	}
}