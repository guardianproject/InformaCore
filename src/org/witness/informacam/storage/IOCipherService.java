package org.witness.informacam.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.MediaHasher;

import com.google.common.cache.LoadingCache;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class IOCipherService extends Service {
	private VirtualFileSystem vfs;
	public static IOCipherService ioCipherService;
	private final IBinder binder = new LocalBinder();
	
	public class LocalBinder extends Binder {
		public IOCipherService getService() {
			return IOCipherService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	public interface IOCipherServiceListener {
		public void onBitmapResaved();
	}
	
	@Override
	public void onCreate() {
		SQLiteDatabase.loadLibs(this);
		
    	java.io.File storageRoot = new java.io.File(getDir(Storage.IOCipher.ROOT, MODE_PRIVATE).getAbsolutePath(), Storage.IOCipher.STORE);
    	vfs = new VirtualFileSystem(storageRoot);
    	
    	DatabaseHelper dh = new DatabaseHelper(this);
    	SQLiteDatabase db = dh.getReadableDatabase(PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.Keys.CURRENT_LOGIN, ""));
    	
    	dh.setTable(db, Tables.Keys.SETUP);
    	Cursor c = dh.getValue(db, new String[] {Device.Keys.AUTH_KEY}, BaseColumns._ID, 1L);
    	if(c != null && c.moveToFirst()) {
    		vfs.mount(c.getString(c.getColumnIndex(Device.Keys.AUTH_KEY)));
    		c.close();
    		ioCipherService = this;
    		db.close();
        	dh.close();
    	} else {
    		vfs = null;
    		db.close();
        	dh.close();
    		Log.e(Storage.LOG, "could not mount virtual file system");
    		this.stopSelf();
    	}
	}
	
	@Override
	public void onDestroy() {
		vfs.unmount();
		super.onDestroy();
	}
	
	public File getFile(Uri uri) {
		List<String> paths = uri.getPathSegments();
		
		File file = null;
		for(File dir : new File("/").listFiles()) {
			if(dir.isDirectory() && dir.getName().equals(paths.get(0))) {
				for(File f : dir.listFiles()) {
					if(f.getName().equals(paths.get(1)))
						file = f;
					
					if(file != null) break;
				}
			}
		}
		
		return file;
	}
	
	public List<JSONObject> getSavedMedia() {
		List<JSONObject> media = new ArrayList<JSONObject>();
		for(File dir : new File("/").listFiles()) {
			if(dir.isDirectory()) {
				for(File f : dir.listFiles()) {
					if(f.getName().equals("manifest.json")) {
						try {
							media.add((JSONObject) new JSONTokener(new String(IOUtility.getBytesFromFile(f))).nextValue());
						} catch (JSONException e) {
							Log.e(Storage.LOG, e.toString());
							e.printStackTrace();
						}
					}
				}
			}
		}
		return media;
	}
	
	public File getFile(String filepath) {
		return getFile(Uri.fromFile(new File(filepath)));
	}
	
	public FileInputStream getFileStream(Uri uri) throws IOException {
		return new FileInputStream(getFile(uri));
	}
	
	public java.io.File moveFromIOCipherToMemory(Uri uri, String filename) throws IOException {
		FileInputStream fis = getFileStream(uri);
		
		java.io.File file = new java.io.File(Storage.FileIO.DUMP_FOLDER, filename);
		java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
		
		int read = 0;
		byte[] bytes = new byte[fis.available()];
		while((read = fis.read(bytes)) != -1)
			fos.write(bytes, 0, read);
		
		fis.close();
		fos.flush();
		fos.close();
		
		return file;
	}
	
	public Uri moveFileToIOCipher(Uri originalUri, int defaultImageHandling) throws NoSuchAlgorithmException, IOException {
		// hash file to make folder for file (if it doesn't already exist)
		byte[] fileBytes = IOUtility.getBytesFromUri(originalUri, this);
		String fileHash = MediaHasher.hash(fileBytes, "SHA-1");
		
		String mimeType = Media.Type.JPEG;
		
		if(originalUri.getLastPathSegment().contains(Media.Type.MP4))
			mimeType = Media.Type.MP4;
		else if(originalUri.getLastPathSegment().contains(Media.Type.MKV))
			mimeType = Media.Type.MKV;
		
		File ioCipherCloneFolder = new File(fileHash);
		if(!ioCipherCloneFolder.exists())
			ioCipherCloneFolder.mkdir();
		
		File ioCipherClone = IOUtility.fileFromBytes(fileBytes, ioCipherCloneFolder.getAbsolutePath() + "/original" + mimeType);
		
		if(defaultImageHandling != Settings.OriginalImageHandling.LEAVE_ORIGINAL_ALONE) {
			IOUtility.deleteFromMediaStore(originalUri, this);
		}

		return Uri.fromFile(ioCipherClone);
	}
	
	public File moveFileToIOCipher(java.io.File version, String rootFolder, boolean delete) {		
		File ioCipherCloneFolder = new File(rootFolder);
		if(!ioCipherCloneFolder.exists())
			ioCipherCloneFolder.mkdir();
		
		byte[] fileBytes = IOUtility.getBytesFromFile(version);
		String forName = version.getName();
		
		if(delete)
			version.delete();
		
		return IOUtility.fileFromBytes(fileBytes, rootFolder + "/" + forName);
	}
	
	public void resaveBitmap(Bitmap bitmap, Uri uri) throws FileNotFoundException {
		if(getFile(uri).exists())
			getFile(uri).delete();
		
		List<String> paths = uri.getPathSegments();
		
		File file = new File(paths.get(0), paths.get(1));
		
		FileOutputStream fos = new FileOutputStream(file);

		boolean result = bitmap.compress(CompressFormat.JPEG, 100, fos);
		
		/*
		if(result)
			InformaService.getInstance().onBitmapResaved();
		else
			Log.d(Storage.LOG, "could not save bitmap");
		*/
	}
	
	public static IOCipherService getInstance() {
		return ioCipherService;
	}
	
	public void loadCache() {
		
	}

	public boolean saveCache(final LogPack originalMetadata, final List<LoadingCache<Long, LogPack>> caches) {
		ExecutorService ex = Executors.newFixedThreadPool(10);
		Future<Boolean> save = ex.submit(new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				String extension = Constants.Media.Type.JPEG;
				File tmpFile = new File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.IMAGE_TMP);
				
				try {
					if(originalMetadata.getInt(Data.Description.MEDIA_TYPE) == Media.Type.VIDEO) {
						extension = Constants.Media.Type.MP4;
						tmpFile = new File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.VIDEO_TMP);
					}
					
					File rootFolder = new File(originalMetadata.getString(Data.Description.ORIGINAL_HASH));
					if(!rootFolder.exists())
						rootFolder.mkdir();
					
					File media = new File(rootFolder, "original" + extension);
					if(!media.exists()) {
						java.io.FileInputStream fis = new java.io.FileInputStream(tmpFile);
						byte[] mediaBytes = new byte[fis.available()];
						fis.read(mediaBytes, 0, fis.available());
						fis.close();
						fis = null;
						
						FileOutputStream fos = new FileOutputStream(media);
						fos.write(mediaBytes);
						fos.close();
						fos = null;
					}
					long lastSaved = System.currentTimeMillis();
					
					File cacheFile = new File(rootFolder, "cache.json");
					if(cacheFile.exists())
						cacheFile.delete();
					
					JSONArray cacheArray = new JSONArray();
					for(LoadingCache<Long, LogPack> cache : caches) {
						JSONArray cacheMap = new JSONArray();
						Iterator<Entry<Long, LogPack>> cIt = cache.asMap().entrySet().iterator();
						while(cIt.hasNext()) {
							JSONObject cacheMapObj = new JSONObject();
							Entry<Long, LogPack> c = cIt.next();
							cacheMapObj.put(String.valueOf(c.getKey()), c.getValue());
							cacheMap.put(cacheMapObj);
						}
						
						JSONObject cacheObj = new JSONObject();
						if(cache.equals(InformaService.getInstance().annotationCache))
							cacheObj.put("annotationCache", cacheMap);
						else if(cache.equals(InformaService.getInstance().suckerCache))
							cacheObj.put("suckerCache", cacheMap);
						
						cacheArray.put(cacheObj);
					}
					
					JSONObject cacheJSON = new JSONObject();
					cacheJSON.put("cache", cacheArray);
					
					String cacheString = cacheJSON.toString();
					Log.d(Storage.LOG, cacheString);
					
					cacheFile = IOUtility.fileFromBytes(cacheString.getBytes(), cacheFile.getAbsolutePath());
					
					JSONObject manifest = new JSONObject();
					
					File manifestFile = new File(rootFolder, "manifest.json");
					
					if(manifestFile.exists()) {
						byte[] manifestBytes = IOUtility.getBytesFromFile(manifestFile);
						manifest = (JSONObject) new JSONTokener(new String(manifestBytes)).nextValue();
						manifestFile.delete();
					} else {
						manifest.put(Manifest.Keys.SIZE, media.length());
						manifest.put(Manifest.Keys.LOCATION_OF_ORIGINAL, media.getAbsolutePath());
						manifest.put(Manifest.Keys.LENGTH, originalMetadata.getInt(Manifest.Keys.LENGTH));
						manifest.put(Manifest.Keys.WIDTH, originalMetadata.getInt(Manifest.Keys.WIDTH));
						manifest.put(Manifest.Keys.MEDIA_TYPE, originalMetadata.getInt(Manifest.Keys.MEDIA_TYPE));
						if(manifest.getInt(Manifest.Keys.MEDIA_TYPE) == Media.Type.VIDEO)
							manifest.put(Manifest.Keys.DURATION, originalMetadata.getInt(Manifest.Keys.DURATION));
					}
					
					manifest.put(Manifest.Keys.LAST_SAVED, lastSaved);
					
					byte[] manifestBytes = manifest.toString().getBytes();
					FileOutputStream fos = new FileOutputStream(manifestFile);
					fos.write(manifestBytes, 0, manifestBytes.length);
					fos.close();
					return true;
				} catch (JSONException e) {
					Log.e(Storage.LOG, e.toString());
					e.printStackTrace();
					return false;
				} catch (FileNotFoundException e) {
					Log.e(Storage.LOG, e.toString());
					e.printStackTrace();
					return false;
				} catch (IOException e) {
					Log.e(Storage.LOG, e.toString());
					e.printStackTrace();
					return false;
				}
				
			}
			
		});
		
		try {
			return save.get();
		} catch (InterruptedException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return false;
		} catch (ExecutionException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return false;
		}
	}

	public java.io.File cipherFileToJavaFile(String filepath, String newPath) {
		File file = getFile(filepath);
		try {
			return this.moveFromIOCipherToMemory(Uri.fromFile(file), newPath);
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}

}
