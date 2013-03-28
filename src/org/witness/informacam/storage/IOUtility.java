package org.witness.informacam.storage;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Base64;
import android.util.Log;

public class IOUtility {
	private final static String LOG = App.Storage.LOG;
	
	public static Uri getUriFromFile(Context context, Uri authority, java.io.File file) {
		Uri uri = null;
		
		ContentResolver cr = context.getContentResolver();
		Cursor c = cr.query(authority, new String[] {BaseColumns._ID}, MediaColumns.DATA + "=?", new String[] {file.getAbsolutePath()}, null);
		if(c != null && c.moveToFirst()) {
			uri = Uri.withAppendedPath(authority, String.valueOf(c.getLong(c.getColumnIndex(BaseColumns._ID))));
			c.close();
		}
		return uri;
	}
	
	public final static byte[] getBytesFromBitmap(Bitmap bitmap, boolean asBase64) {
		return getBytesFromBitmap(bitmap, 100, asBase64);
	}
	
	public final static byte[] getBytesFromBitmap(Bitmap bitmap, int quality, boolean asBase64) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
		if(asBase64) {
			return Base64.encode(baos.toByteArray(), Base64.DEFAULT);
		} else {
			return baos.toByteArray();
		}
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

	public final static Bitmap getBitmapFromFile(String pathToFile, int source) {
		byte[] bytes = null;
		Bitmap bitmap = null;
		
		switch(source) {
		case Type.IOCIPHER:
			try {
				info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(pathToFile);
				info.guardianproject.iocipher.FileInputStream fis = new info.guardianproject.iocipher.FileInputStream(file);
				
				bytes = new byte[fis.available()];
				fis.read(bytes);
				fis.close();
				
				bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				bytes = null;
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			break;
		}
		
		return bitmap;
		
	}
}
