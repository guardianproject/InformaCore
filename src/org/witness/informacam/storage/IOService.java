package org.witness.informacam.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import info.guardianproject.iocipher.VirtualFileSystem;


import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.models.Model;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class IOService extends Service {
	private final IBinder binder = new LocalBinder();
	private static IOService IOService = null;

	private VirtualFileSystem vfs = null;
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
		try {
			java.io.FileOutputStream fos = openFileOutput(file.getName(), MODE_PRIVATE);
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

	public boolean saveBlob(byte[] data, info.guardianproject.iocipher.File file) {
		Log.d(LOG, "touch " + file.getAbsolutePath());
		try {
			info.guardianproject.iocipher.FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();
			return true;

		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
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

	public static boolean zipFiles(Map<String, byte[]> elements, String fileName, int destination) {
		ZipOutputStream zos = null;
		try {
			switch(destination) {
			case Type.IOCIPHER:
				zos = new ZipOutputStream(new info.guardianproject.iocipher.FileOutputStream(fileName));
				break;
			case Type.INTERNAL_STORAGE:
				zos = new ZipOutputStream(new java.io.FileOutputStream(fileName));
				break;
			}
			
			Iterator<Entry<String, byte[]>> i = elements.entrySet().iterator();
			while(i.hasNext()) {
				Entry<String, byte[]> file = i.next();
				ZipEntry ze = new ZipEntry(file.getKey());
				zos.putNextEntry(ze);

				zos.write(file.getValue());
				zos.flush();
			}
			
			zos.close();
			return true;
		} catch(IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}

	public byte[] getBytes(String pathToData, int source) {
		return getBytes(pathToData, source, null);
	}

	public byte[] getBytes(String pathToData, int source, Context c) {
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
			if(c != null) {
				try {
					InputStream is = c.getAssets().open(pathToData, MODE_PRIVATE);
					bytes = new byte[is.available()];
					is.read(bytes);
					is.close();
				} catch (IOException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
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
		default:
			return false;
		}

	}

	public void unmount() {
		vfs.unmount();		
	}
}
