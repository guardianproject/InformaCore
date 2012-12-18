package org.witness.informacam.utils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Storage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;

public class InformaMediaScanner implements MediaScannerConnectionClient {
	private static String LOG = Storage.LOG;
	
	private MediaScannerConnection msc;
	private File f;
	private Activity a;
	
	public interface OnMediaScannedListener {
		public void onMediaScanned(Uri uri);
	}
	
	public InformaMediaScanner(Activity a, File f) {
		this.f = f;
		this.a = a;
		msc = new MediaScannerConnection(a, this);
		msc.connect();
	}
	
	@Override
	public void onMediaScannerConnected() {
		msc.scanFile(f.getAbsolutePath(), null);
	}

	@Override
	public void onScanCompleted(String path, Uri uri) {
		((OnMediaScannedListener) a).onMediaScanned(uri);
	}
	
	public static Uri getUriFromFile(Context context, File file) {
		Uri uri = null;
		
		ContentResolver cr = context.getContentResolver();
		Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {BaseColumns._ID}, MediaStore.Images.Media.DATA + "=?", new String[] {file.getAbsolutePath()}, null);
		if(c != null && c.moveToFirst()) {
			uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(c.getColumnIndex(BaseColumns._ID))));
			c.close();
		}
		return uri;
	}
	
	public static void testDCIMManagement() {
		// get last picture taken
	}
	
	public static void setDCIMManagementType() {
		
	}
	
	public static int getDCIMManagementType() {
		return 0;
	}
	
	public static void doScanForDeletion(final Context c, final File file) {
		MediaScannerConnection.scanFile(c, new String[] {file.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
			
			@Override
			public void onScanCompleted(String path, Uri uri) {
				file.delete();
				c.getContentResolver().delete(uri, null, null);
			}
		});
	}

	public static DCIMDescriptor getDCIMDescriptor(Context c) {
		File dcim = new File(Environment.getExternalStorageDirectory(), "DCIM");
		if(dcim.exists()) {
			return new DCIMDescriptor(c, dcim);
		}
			
		return null;
	}
	
	public static class DCIMDescriptor extends JSONObject {
		Context c;
		
		@SuppressLint("DefaultLocale")
		DCIMDescriptor(Context c, File dcim) {
			this.c = c;
			for(File f : dcim.listFiles()) {
				try {
					if(f.isDirectory() && !f.getName().startsWith("."))
						put(f.getName().toLowerCase(), analyse(f));
					else {
						// should not be in DCIM root but...
					}
				} catch(JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
			}
			
		}
		
		JSONObject analyse(File folder_) throws JSONException {
			
			File last_file = null;
			for(File file : folder_.listFiles()) {
				if(file.isDirectory())
					continue;
				
				if(last_file == null) {
					last_file = file;
					continue;
				}
				
				if(file.lastModified() > last_file.lastModified())
					last_file = file;
			}
			
			if(last_file == null)
				return null;
			
			
			JSONObject last_file_ = new JSONObject();
			/*
			 * 
			 * {"dcim":[
			 * 	"camera": {
			 * 		"num_files":22,
			 * 		"last_file": {
			 * 			"path":"/sdcard/DCIM/Camera/1239_IMG.jpg",
			 * 			"uri":"/images/conten/pictur/123",
			 * 			"size":123837,
			 * 			"hash":"130985rcvso9235",
			 * 			"last_modified":123934674
			 * 		}
			 * 	}
			 * ]}
			 * 
			 * 
			 * 
			 */
			last_file_.put("path", last_file.getAbsolutePath());
			last_file_.put("size", last_file.length());
			last_file_.put("last_modified", last_file.lastModified());
			last_file_.put("uri", getUriFromFile(c, last_file));
			try {
				last_file_.put("hash", MediaHasher.hash(last_file, "SHA-1"));
			} catch (NoSuchAlgorithmException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			
			JSONObject folder = new JSONObject();
			folder.put("num_files", folder_.listFiles().length);
			folder.put("last_file", last_file_);
			
			return folder;
		}
	}

}
