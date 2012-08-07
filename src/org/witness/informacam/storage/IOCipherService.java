package org.witness.informacam.storage;

import info.guardianproject.iocipher.VirtualFileSystem;

import java.io.File;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class IOCipherService extends Service {
	private VirtualFileSystem vfs;
	private static IOCipherService ioCipherService;
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
		File dumpFolder = new File(Storage.FileIO.DUMP_FOLDER);
    	if(!dumpFolder.exists())
    		dumpFolder.mkdir();
    	
    	File storageRoot = new File(dumpFolder, Storage.IOCipher.STORE);
    	
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
	
	public IOCipherService getInstance() {
		return ioCipherService;
	}

}
