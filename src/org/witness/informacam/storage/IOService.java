package org.witness.informacam.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.VirtualFileSystem;

import org.witness.informacam.InformaCam;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class IOService extends Service {
	private final IBinder binder = new LocalBinder();
	private static IOService IOService = null;

	private VirtualFileSystem vfs = null;
	private DCIMObserver dcimObserver = null;
	private InformaCam informaCam = InformaCam.getInstance();
	
	private List<java.io.File> cleanupQueue = new Vector<java.io.File>();

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
		
		for(java.io.File f : cleanupQueue) {
			Log.d(LOG, "removing unsafe file: " + f.getAbsolutePath());
			f.delete();
		}
		
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.IO_SERVICE).setAction(Actions.DISASSOCIATE_SERVICE));
		
	}

	public static IOService getInstance() {
		return IOService;
	}
	
	public boolean saveBlob(byte[] data, java.io.File file, boolean isPublic) {
		if(!isPublic) {
			return saveBlob(data, file);
		} else {
			try {
				java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
				fos.write(data);
				fos.flush();
				fos.close();
				
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
		byte[] bytes = new byte[0];

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
				Log.d(LOG, "no, no bytes");
				return null;
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (Exception e) {
				Log.e(LOG, e.toString());
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

		Log.d(LOG, "bytes here: " + bytes.length);
		return bytes;
	}
	
	public java.io.File getPublicCredentials() {
		byte[] publicCredentialsBytes = getBytes(Models.IUser.PUBLIC_CREDENTIALS, Type.IOCIPHER);
		if(publicCredentialsBytes != null) {
			try {
				java.io.File externalDir = new java.io.File(Storage.EXTERNAL_DIR);
				if(!externalDir.exists()) {
					externalDir.mkdir();
				}
				
				java.io.File publicCredentials = new java.io.File(Storage.EXTERNAL_DIR, "publicCredentials.zip");
				java.io.FileOutputStream fis = new java.io.FileOutputStream(publicCredentials);
				
				fis.write(publicCredentialsBytes);
				fis.flush();
				fis.close();
				return publicCredentials;
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		return null;
	}

	public boolean initIOCipher(String authToken) {
		try {
			java.io.File storageRoot = new java.io.File(getExternalFilesDir(Storage.ROOT).getAbsolutePath(), Storage.IOCIPHER);
			vfs = new VirtualFileSystem(storageRoot);
			vfs.mount(authToken);
			
			Log.d(LOG, "MOUNTED IOCIPHER");
			
			info.guardianproject.iocipher.File organizationRoot = new info.guardianproject.iocipher.File(Storage.ORGS_ROOT);
			if(!organizationRoot.exists()) {
				organizationRoot.mkdir();
			}
			
			info.guardianproject.iocipher.File formsRoot = new info.guardianproject.iocipher.File(Storage.FORM_ROOT);
			if(!formsRoot.exists()) {
				formsRoot.mkdir();
			}

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
			if(file.isDirectory()) {
				for(info.guardianproject.iocipher.File f : file.listFiles()) {
					f.delete();
				}
			}
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
		if(informaCam == null) {
			informaCam = InformaCam.getInstance();
		}
		
		dcimObserver = new DCIMObserver(informaCam.a);
	}
	
	public void stopDCIMObserver() {
		dcimObserver.destroy();
	}

	public boolean isMounted() {
		if(vfs != null) {
			return vfs.isMounted();
		}
		
		return false;
	}
}
