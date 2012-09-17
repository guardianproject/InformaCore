package org.witness.informacam.transport;

import info.guardianproject.iocipher.File;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.j3m.J3M.J3MManifest;
import org.witness.informacam.j3m.J3M.J3MPackage;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.Uploader;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class UploaderService extends Service {
	private final IBinder binder = new LocalBinder();
	private static UploaderService uploaderService;
	
	DatabaseService databaseService = DatabaseService.getInstance();
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	Queue<J3MManifest> queue;
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	public class LocalBinder extends Binder {
		public UploaderService getService() {
			return UploaderService.this;
		}
	}
	
	public static UploaderService getInstance() {
		return uploaderService;
	}
	
	Thread queueMonitor = new Thread(
			new Runnable() {
				long timeIdle;
				
				@Override
				public void run() {
					upload();
				}
				
				private void upload() {
					if(queue.isEmpty())
						sleep();
					else {
						if(uploadChunk(queue.peek()))
							upload();
					}
				}
				
				private void sleep() {
					timeIdle = System.currentTimeMillis();
					Log.d(Transport.LOG, "just waiting for an upload...");
					do {
						// do nothing, just wait!
					} while(queue.isEmpty());
					Log.d(Transport.LOG, "idle time: " + (System.currentTimeMillis() - timeIdle));
					timeIdle = System.currentTimeMillis();
					upload();
				}
			}
	);
	
	private void initUploadsFromDatabase() {
		dh.setTable(db, Tables.Keys.MEDIA);
		Cursor uploads = dh.getValue(db, new String[] {Media.Keys.J3M_MANIFEST}, null, null);
		if(uploads != null && uploads.moveToFirst()) {
			while(!uploads.isAfterLast()) {
				byte[] jmd = null;
				try {
					jmd = uploads.getBlob(uploads.getColumnIndex(Media.Keys.J3M_MANIFEST));
					Log.d(Transport.LOG, new String(jmd));
				} catch(Exception e) {
					uploads.moveToNext();
					continue;
				}
				
				try {
					final J3MManifest j3mManifest = new J3MManifest((JSONObject) new JSONTokener(new String(jmd)).nextValue());
					final int chunks_uploaded = j3mManifest.getInt(Media.Manifest.Keys.LAST_TRANSFERRED);
					int total_chunks = j3mManifest.getInt(Media.Manifest.Keys.TOTAL_CHUNKS);
					
					if(chunks_uploaded != (total_chunks - 1)) {
						new Thread(new Runnable() {
							@Override
							public void run() {
								queue.add(j3mManifest);
							}
						}).start();
						
					} else {
						Log.d(Transport.LOG, "actually you have all your uploads done");
						if(!j3mManifest.has(Media.Manifest.UPLOADED_FLAG) || !j3mManifest.getBoolean(Media.Manifest.UPLOADED_FLAG)) {
							List<Integer> missing = checkForMissingUploads(j3mManifest);
							for(int m : missing) {
								if(m > -1) {
									if(uploadPatch(j3mManifest, m + "_.j3mtorrent")) {
										j3mManifest.put(Media.Manifest.UPLOADED_FLAG, true);
										j3mManifest.save();
									}
								} else {
									int ec = -1 * m;
									switch(ec) {
									case Transport.Result.ErrorCodes.AUTH_FAILURE:	// upload was actually completed.
										j3mManifest.put(Media.Manifest.UPLOADED_FLAG, true);
										j3mManifest.save();
										break;
									}
								}
							}
						} else {
							Log.d(Transport.LOG, "this manifest hasn't seen the upload flag and/or is totally uploaded");
						}
					}
				} catch (JSONException e) {
					Log.e(Transport.LOG, e.toString());
					e.printStackTrace();
				}
				uploads.moveToNext();
			}
			uploads.close();
		}
	}
	
	private List<Integer> checkForMissingUploads(J3MManifest j3mManifest) throws JSONException {
		List<Integer> missing = new ArrayList<Integer>();
		
		Map<String, Object> postData = new HashMap<String, Object>();
		postData.put(Uploader.Keys.AUTH_TOKEN, j3mManifest.getString(Media.Manifest.Keys.AUTH_TOKEN));
		postData.put(Uploader.Keys.CLIENT_PGP, j3mManifest.getString(Uploader.Keys.CLIENT_PGP));
		postData.put(Uploader.Keys.CHECK_FOR_MISSING_TORRENTS, j3mManifest.getString(Media.Keys.J3M_BASE));
		
		String url = j3mManifest.getString(Transport.Keys.URL);
		long pkcs12Id = j3mManifest.getLong(Transport.Keys.CERTS);
		
		String result = HttpUtility.executeHttpsPost(this, url, postData, Transport.MimeTypes.TEXT, pkcs12Id, null, null, null);
		JSONObject res = parseResult(result);
		if(res.getString(Transport.Keys.RESULT).equals(Transport.Result.OK)) {
			JSONArray m = res.getJSONArray(Transport.Keys.MISSING_TORRENTS);
			for(int i=0; i<m.length(); i++)
				missing.add(m.getInt(i));
		} else {
			// handle not having a result with some silly trickery
			missing.add(-1 * res.getInt(Transport.Keys.ERROR_CODE));
			Log.d(Transport.LOG, res.toString());
		}
		
		return missing;
	}
	
	public void requestTicket(J3MPackage j3mPackage) {
		Map<String, Object> postData = new HashMap<String, Object>();

		postData.put(Uploader.Keys.J3M, j3mPackage.j3m);
		String result = HttpUtility.executeHttpsPost(this, j3mPackage.url, postData, Transport.MimeTypes.JSON, j3mPackage.pkcs12Id, null, null, null);

		try {
			JSONObject res = parseResult(result);
			
			J3MManifest j3mmanifest = new J3MManifest(j3mPackage.root, j3mPackage.pgpFingerprint, j3mPackage.chunk_num);
			String authToken = res.getJSONObject(Transport.Keys.BUNDLE).getString(Media.Manifest.Keys.AUTH_TOKEN);
			
			//j3mbase, totalchunks, lasttransferred included by default
			j3mmanifest.put(Transport.Keys.URL, j3mPackage.url);
			j3mmanifest.put(Transport.Manifest.Keys.AUTH_TOKEN, authToken);
			j3mmanifest.put(Transport.Keys.CERTS, j3mPackage.pkcs12Id);
			j3mmanifest.save();
			queue.add(j3mmanifest);
			queueMonitor.run();
		} catch (JSONException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		} catch(NullPointerException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	private boolean uploadPatch(J3MManifest j3mManifest, String chunkName) {
		try {
			byte[] chunk = IOUtility.getBytesFromFile(new File(j3mManifest.getString(Transport.Manifest.Keys.J3MBase) + "/j3m/" + chunkName));
			
			Map<String, Object> postData = new HashMap<String, Object>();
			postData.put(Uploader.Keys.AUTH_TOKEN, j3mManifest.getString(Media.Manifest.Keys.AUTH_TOKEN));
			postData.put(Uploader.Keys.CLIENT_PGP, j3mManifest.getString(Uploader.Keys.CLIENT_PGP));
			
			String url = j3mManifest.getString(Transport.Keys.URL);
			long pkcs12Id = j3mManifest.getLong(Transport.Keys.CERTS);
			
			String result = HttpUtility.executeHttpsPost(this, url, postData, Transport.MimeTypes.TEXT, pkcs12Id, chunk, chunkName, Transport.MimeTypes.OCTET_STREAM);
			Log.d(Transport.LOG, result);
			
			JSONObject res = parseResult(result);
			if(res != null) {
				Log.d(Transport.LOG, "updated with a patch");
			} else if(res.has(Transport.Keys.ERROR_CODE)){
				switch(res.getInt(Transport.Keys.ERROR_CODE)) {
				case Transport.Result.ErrorCodes.DUPLICATE_J3M_TORRENT:
					return false;
				case Transport.Result.ErrorCodes.NO_UPLOADS_MISSING:
					return true;
				}
				
			}
			return true;
		} catch(JSONException e) {
			return false;
		}
	}
	
	private boolean getRequiredData(J3MManifest j3mManifest) {
		try {
			String host = j3mManifest.getString(Transport.Keys.URL);
			long pkcs12Id = j3mManifest.getLong(Transport.Keys.CERTS);
			
			Map<String, Object> postData = new HashMap<String, Object>();
			postData.put(Transport.Keys.GET_REQUIREMENTS, j3mManifest.getString(Uploader.Keys.CLIENT_PGP));
			
			String result = HttpUtility.executeHttpsPost(this, host, postData, Transport.MimeTypes.TEXT, pkcs12Id);
			Log.d(Transport.LOG, result);
			JSONObject res = parseResult(result);
			
			if(res != null) {
				JSONArray requirements = res.getJSONObject(Transport.Keys.BUNDLE).getJSONArray(Transport.Keys.REQUIREMENTS);
				for(int r=0; r<requirements.length(); r++) {
					switch(requirements.getInt(r)) {
					case Transport.Result.ErrorCodes.BASE_IMAGE_REQUIRED:
						dh.setTable(db, Tables.Keys.SETUP);
						Cursor b = dh.getValue(db, new String[] {Settings.Device.Keys.BASE_IMAGE}, BaseColumns._ID, 1L);
						if(b!= null && b.moveToFirst()) {
							byte[] baseImage = b.getBlob(b.getColumnIndex(Settings.Device.Keys.BASE_IMAGE));
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							BitmapFactory.decodeByteArray(baseImage, 0, baseImage.length).compress(CompressFormat.JPEG, 100, baos);
							b.close();
							uploadSupportingData(j3mManifest, Transport.Keys.BASE_IMAGE, baos.toByteArray(), "baseImage.jpg");
						} else {
							Log.d(Transport.LOG, "could not get base image");
						}
					case Transport.Result.ErrorCodes.PGP_KEY_REQUIRED:
						dh.setTable(db, Tables.Keys.SETUP);
						Cursor p = dh.getValue(db, new String[] {Settings.Device.Keys.SECRET_KEY}, BaseColumns._ID, 1L);
						if(p != null && p.moveToFirst()) {
							byte[] secretKey = p.getBlob(p.getColumnIndex(Settings.Device.Keys.SECRET_KEY));
							p.close();
							
							try {
								byte publicKey[] = KeyUtility.extractSecretKey(secretKey).getPublicKey().getEncoded();
								uploadSupportingData(j3mManifest, Transport.Keys.PGP_KEY_ENCODED, publicKey, "publicKey.asc");
							} catch (IOException e) {
								Log.e(Transport.LOG, "could not parse found pgp key\n" + e.toString());
								e.printStackTrace();
							}
						} else {
							Log.e(Transport.LOG, "could not find pgp key");
						}
					}
				}
				return true;
			} else {
				return false;
			}
		} catch(JSONException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
			return false;
		}
	}
	
	
	private boolean uploadSupportingData(J3MManifest j3mManifest, String supportingDataType, byte[] chunk, String chunkName) {
		try {
			Map<String, Object> postData = new HashMap<String, Object>();
			postData.put(Uploader.Keys.CLIENT_PGP, j3mManifest.getString(Uploader.Keys.CLIENT_PGP));
			postData.put(Uploader.Keys.SUPPORTING_DATA, supportingDataType);
			
			String url = j3mManifest.getString(Transport.Keys.URL);
			long pkcs12Id = j3mManifest.getLong(Transport.Keys.CERTS);
			
			String result = HttpUtility.executeHttpsPost(this, url, postData, Transport.MimeTypes.TEXT, pkcs12Id, chunk, chunkName, Transport.MimeTypes.OCTET_STREAM);
			Log.d(Transport.LOG, result);
			
			JSONObject res = parseResult(result);
			if(res != null) {
				Log.d(Transport.LOG, "uploaded supporting data");
				return true;
			} else
				return false;
		} catch(JSONException e) {
			return false;
		}
	}
	
	private boolean uploadChunk(J3MManifest j3mManifest) {
		try {
			int lastTransferred = j3mManifest.getInt(Transport.Manifest.Keys.LAST_TRANSFERRED);
			
			String chunkName = (lastTransferred + 1) + "_.j3mtorrent";
			byte[] chunk = IOUtility.getBytesFromFile(new File(j3mManifest.getString(Transport.Manifest.Keys.J3MBase) + "/j3m/" + chunkName));
			
			if(chunk == null)
				return false;
					
			Map<String, Object> postData = new HashMap<String, Object>();
			postData.put(Uploader.Keys.AUTH_TOKEN, j3mManifest.getString(Media.Manifest.Keys.AUTH_TOKEN));
			postData.put(Uploader.Keys.CLIENT_PGP, j3mManifest.getString(Uploader.Keys.CLIENT_PGP));
			
			String url = j3mManifest.getString(Transport.Keys.URL);
			long pkcs12Id = j3mManifest.getLong(Transport.Keys.CERTS);
			
			String result = HttpUtility.executeHttpsPost(this, url, postData, Transport.MimeTypes.TEXT, pkcs12Id, chunk, chunkName, Transport.MimeTypes.OCTET_STREAM);
			Log.d(Transport.LOG, result);
			
			JSONObject res = parseResult(result);
			if(res != null) {
				// if its already in the queue, pull it out
				
				if(res.getString(Transport.Keys.RESULT).equals(Transport.Result.OK)) {
					if(queue.contains(j3mManifest)) {
						queue.remove(j3mManifest);
						Log.d(Transport.LOG, "manifest still in queue: " + j3mManifest.toString());
					}
					
					JSONObject bundle = res.getJSONObject(Transport.Keys.BUNDLE);
					if(bundle.has(Transport.Keys.REQUIREMENTS)) {
						
					}
				} else {
					switch(res.getInt(Transport.Keys.ERROR_CODE)) {
					case Transport.Result.ErrorCodes.DUPLICATE_J3M_TORRENT:
						if(queue.contains(j3mManifest))
							queue.remove(j3mManifest);
						
						lastTransferred++;
						Log.d(Transport.LOG, "this one failed. last transferred upped to " + lastTransferred);
						break;
					}
						
				}
				
				// modify the new bytes and add to queue
				j3mManifest.put(Transport.Manifest.Keys.LAST_TRANSFERRED, (lastTransferred + 1));
				j3mManifest.save();
				queue.add(j3mManifest);
				Log.d(Transport.LOG, "queue size: " + queue.size());
				return true;
			}
		} catch(NullPointerException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		}
		return false;
	}
	
	private JSONObject parseResult(String result) {
		try {
			JSONObject res = ((JSONObject) new JSONTokener(result).nextValue()).getJSONObject(Transport.Keys.RES);
			Log.d(Transport.LOG, res.toString());
			return res;
		} catch(JSONException e) {
			try {
				Log.e(Transport.LOG,"the result did not have a res object so probably a fail!");
				return (JSONObject) new JSONTokener(result).nextValue();
			} catch(JSONException e1) {
				
				return null;
			}
		}
		catch(ClassCastException e) {
			Log.e(Transport.LOG, "not json but here it is anyway:\n" + result);
		}
		return null;
	}
	
	@Override
	public void onCreate() {
		queue = new LinkedList<J3MManifest>();
		dh = databaseService.getHelper();
		db = databaseService.getDb();
		
		queueMonitor.start();
		initUploadsFromDatabase();
		
		uploaderService = this;
	}
	
	private void saveQueueChanges() {
		Log.d(Transport.LOG, "SAVING QUEUE CHANGES!");
		Iterator<J3MManifest> qIt = queue.iterator();
		dh.setTable(db, Tables.Keys.MEDIA);
		while(qIt.hasNext()) {
			try {
				J3MManifest j3mManifest = qIt.next();
				ContentValues cv = new ContentValues();
				cv.put(Media.Keys.J3M_MANIFEST, j3mManifest.toString());
				db.update(dh.getTable(), cv, Media.Keys.J3M_BASE + "=?", new String[] {j3mManifest.getString(Media.Keys.J3M_BASE)});
			} catch(JSONException e) {
				Log.e(Transport.LOG, e.toString());
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		saveQueueChanges();
		Log.d(Uploader.LOG, "uploader service destroyed.");
	}

	public void restart() {
		queueMonitor.run();
		
	}
}
