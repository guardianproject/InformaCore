package org.witness.informacam.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import info.guardianproject.iocipher.VirtualFileSystem;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.models.Model;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class IOService extends Service {
	private final IBinder binder = new LocalBinder();
	private static IOService IOService = null;

	private VirtualFileSystem vfs = null;
	private DCIMObserver dcimObserver = null;
	private InformaCam informaCam = InformaCam.getInstance();

	private final static String LOG = App.Storage.LOG;

	public class LocalBinder extends Binder {
		public IOService getService() {
			return IOService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		IOService = this;
		sendBroadcast(new Intent().setAction(Actions.ASSOCIATE_SERVICE).putExtra(Codes.Keys.SERVICE, Codes.Routes.IO_SERVICE));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if(vfs != null) {
			vfs.unmount();
		}
	}

	public static IOService getInstance() {
		return IOService;
	}
	
	public boolean saveBlob(byte[] data, java.io.File file) {
		return saveBlob(data, file, false, null);
	}

	public boolean saveBlob(byte[] data, java.io.File file, boolean delete, String uri) {
		try {
			java.io.FileOutputStream fos = openFileOutput(file.getName(), MODE_PRIVATE);
			fos.write(data);
			fos.flush();
			fos.close();
			
			if(delete) {
				file.delete();
			}
			
			if(uri != null) {
				getContentResolver().delete(Uri.parse(uri), null, null);
			}
			
			return true;

		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}
	
	public boolean saveBlob(byte[] data, info.guardianproject.iocipher.File file) {
		return saveBlob(data, file, false, null);
	}

	public boolean saveBlob(byte[] data, info.guardianproject.iocipher.File file, boolean delete, String uri) {

		if(vfs == null) {
			Log.d(LOG, "also, VFS IS NULL SO...");
			
			if(!informaCam.attemptLogin()) {
				informaCam.promptForLogin(Codes.Routes.RETRY_SAVE, data, file);
				return false;
			}
		}
		
		Log.d(LOG, "touch " + file.getAbsolutePath());
		try {
			info.guardianproject.iocipher.FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();
			
			if(delete) {
				file.delete();
			}
			
			if(uri != null) {
				getContentResolver().delete(Uri.parse(uri), null, null);
			}
			
			return true;

		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}

	public boolean saveBlob(Model model, java.io.File file) {
		return saveBlob(model.asJson().toString().getBytes(), file);
	}

	public boolean saveBlob(Model model, info.guardianproject.iocipher.File file) {
		return saveBlob(model.asJson().toString().getBytes(), file);
	}

	public byte[] getBytes(String pathToData, int source) {
		byte[] bytes = null;

		switch(source) {
		case Storage.Type.INTERNAL_STORAGE:
			java.io.FileInputStream fis;
			
			try {
				fis = openFileInput(pathToData);
				bytes = new byte[fis.available()];
				fis.read(bytes);
				fis.close();
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			break;
		case Storage.Type.IOCIPHER:
			
			if(vfs == null) {
				Log.d(LOG, "also, VFS IS NULL SO...");
				
				if(!informaCam.attemptLogin()) {
					informaCam.promptForLogin();
					return null;
				}
			}
			
			info.guardianproject.iocipher.FileInputStream iFis;
			info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(pathToData);

			try {
				iFis = new info.guardianproject.iocipher.FileInputStream(file);
				bytes = new byte[iFis.available()];
				iFis.read(bytes);
				iFis.close();
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			break;
		case Storage.Type.APPLICATION_ASSET:
			try {
				InputStream is = getAssets().open(pathToData, MODE_PRIVATE);
				bytes = new byte[is.available()];
				is.read(bytes);
				is.close();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			break;
		case Storage.Type.CONTENT_RESOLVER:
			break;
		case Storage.Type.FILE_SYSTEM:
			try {
				java.io.File file_ = new java.io.File(pathToData);
				java.io.FileInputStream fis_ = new java.io.FileInputStream(file_);
				
				bytes = new byte[fis_.available()];
				fis_.read(bytes);
				fis_.close();
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			break;
		}

		return bytes;
	}

	public boolean initIOCipher(String authToken) {
		try {
			java.io.File storageRoot = new java.io.File(getDir(Storage.ROOT, MODE_PRIVATE).getAbsolutePath(), Storage.IOCIPHER);
			vfs = new VirtualFileSystem(storageRoot);
			vfs.mount(authToken);
			
			Log.d(LOG, "MOUNTED IOCIPHER");

			return true;
		} catch(IllegalArgumentException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}

	public boolean delete(String pathToFile, int source) {
		switch(source) {
		case Storage.Type.INTERNAL_STORAGE:
			return deleteFile(pathToFile);
		case Storage.Type.IOCIPHER:
			info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(pathToFile);
			return file.delete();
		case Storage.Type.CONTENT_RESOLVER:
			return getContentResolver().delete(Uri.parse(pathToFile), null, null) > 0 ? true : false;
		default:
			return false;
		}

	}

	public void unmount() {
		vfs.unmount();
	}

	public void startDCIMObserver() {
		dcimObserver = new DCIMObserver(this);
	}
	
	public void stopDCIMObserver() {
		dcimObserver.destroy();
	}
}
