package org.witness.informacam.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.informa.InformaService;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.MediaHasher;

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
		Log.d(Storage.LOG, "making file: " + ioCipherClone.getAbsolutePath());
		
		if(defaultImageHandling != Settings.OriginalImageHandling.LEAVE_ORIGINAL_ALONE) {
			IOUtility.deleteFromMediaStore(originalUri, this);
		}

		return Uri.fromFile(ioCipherClone);
	}
	
	public File moveFileToIOCipher(java.io.File version, String rootFolder, boolean delete) {
		Log.d(Storage.LOG, "moving to iocipher at " + rootFolder + "/" + version.getName());
		
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
		Log.d(Storage.LOG, file.getAbsolutePath());
		
		FileOutputStream fos = new FileOutputStream(file);

		boolean result = bitmap.compress(CompressFormat.JPEG, 100, fos);
		Log.d(Storage.LOG, "result from this is " + result);
		
		if(result)
			InformaService.getInstance().onBitmapResaved();
		else
			Log.d(Storage.LOG, "could not save bitmap");
	}
	
	public static IOCipherService getInstance() {
		return ioCipherService;
	}

}
