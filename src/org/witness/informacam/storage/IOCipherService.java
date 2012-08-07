package org.witness.informacam.storage;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.VirtualFileSystem;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.IOUtility;
import org.witness.informacam.utils.MediaHasher;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.MediaStore;
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
	
	@Override
	public void onCreate() {
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
	
	public FileInputStream getFileStream(Uri uri) throws IOException {
		return new FileInputStream(getFile(uri));
	}
	
	public Uri moveFileToIOCipher(Uri originalUri, int defaultImageHandling) throws NoSuchAlgorithmException, IOException {
		// hash file to make folder for file (if it doesn't already exist)
		byte[] fileBytes = IOUtility.getBytesFromUri(originalUri, this);
		String fileHash = MediaHasher.hash(fileBytes, "SHA-1");
		
		String mimeType = Media.Type.JEPG;
		
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
	
	public static IOCipherService getInstance() {
		return ioCipherService;
	}

}
