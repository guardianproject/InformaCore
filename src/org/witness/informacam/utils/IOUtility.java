package org.witness.informacam.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Exif;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

public class IOUtility {
	public final static byte[] getBytesFromBitmap(Bitmap bitmap, int quality) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
		return baos.toByteArray();
	}
	
	public final static Bitmap getBitmapFromBytes(byte[] bytes, boolean isBase64) {
		byte[] b = bytes;
		if(isBase64)
			b = Base64.decode(bytes);
		
		Bitmap bitmap = BitmapFactory.decodeByteArray(b,0,b.length);
		Matrix m = new Matrix();
		m.postScale(80f/bitmap.getWidth(), 80f/bitmap.getHeight());
		
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
		
	}
	
	public final static byte[] getBytesFromFile(File file) {
		byte[] bytes = new byte[(int) file.length()];
		
		try {
			RandomAccessFile raf;
			raf = new RandomAccessFile(file, "r");
			raf.readFully(bytes);
		} catch (FileNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		
		
		return bytes;
	}
	
	public final static byte[] getBytesFromFile(info.guardianproject.iocipher.File file) {
		byte[] bytes = new byte[(int) file.length()];
		
		try {
			RandomAccessFile raf;
			raf = new RandomAccessFile(file, "r");
			raf.readFully(bytes);
		} catch (FileNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		
		
		return bytes;
	}
	
	public final static info.guardianproject.iocipher.File fileFromBytes(byte[] bytes, String fileName) {
		info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(fileName);
		
		info.guardianproject.iocipher.FileOutputStream fos;
		try {
			fos = new info.guardianproject.iocipher.FileOutputStream(fileName);
			fos.write(bytes);
			fos.close();
			return file;
		} catch (FileNotFoundException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		
	}
	
	public final static LogPack getMetadata(String filepath, String mimeType) {
		try {
			LogPack logPack = new LogPack(Informa.CaptureEvent.Keys.TYPE, Informa.CaptureEvent.METADATA_CAPTURED);
			
			if(mimeType.equals(Media.Type.MIME_TYPE_JPEG)) {
				ExifInterface ei = new ExifInterface(filepath);
				
				logPack.put(Exif.TIMESTAMP, ei.getAttribute(Exif.TIMESTAMP));
				logPack.put(Exif.APERTURE, ei.getAttribute(Exif.APERTURE));
				logPack.put(Exif.EXPOSURE, ei.getAttribute(Exif.EXPOSURE));
				logPack.put(Exif.FLASH, ei.getAttributeInt(Exif.FLASH, Informa.Keys.NOT_REPORTED));
				logPack.put(Exif.FOCAL_LENGTH, ei.getAttributeInt(Exif.FOCAL_LENGTH, Informa.Keys.NOT_REPORTED));
				logPack.put(Exif.IMAGE_LENGTH, ei.getAttributeInt(Exif.IMAGE_LENGTH, Informa.Keys.NOT_REPORTED));
				logPack.put(Exif.IMAGE_WIDTH, ei.getAttributeInt(Exif.IMAGE_WIDTH, Informa.Keys.NOT_REPORTED));
				logPack.put(Exif.ISO, ei.getAttribute(Exif.ISO));
				logPack.put(Exif.MAKE, ei.getAttribute(Exif.MAKE));
				logPack.put(Exif.MODEL, ei.getAttribute(Exif.MODEL));
				logPack.put(Exif.ORIENTATION, ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
				logPack.put(Exif.WHITE_BALANCE, ei.getAttributeInt(Exif.WHITE_BALANCE, Informa.Keys.NOT_REPORTED));
				
				logPack.put(Data.Description.MEDIA_TYPE, Media.Type.IMAGE);
				logPack.put(Data.Description.ORIGINAL_HASH, MediaHasher.hash(new File(filepath), "SHA-1"));
				
				Log.d(App.LOG, logPack.toString());
			} else if(mimeType.equals(Media.Type.MIME_TYPE_MP4)) {
				
				logPack.put(Data.Description.MEDIA_TYPE, Media.Type.VIDEO);
				logPack.put(Data.Description.ORIGINAL_HASH, MediaHasher.hash(new File(filepath), "SHA-1"));
			}
			return logPack;
		} catch (IOException e) {
			return null;
		} catch (JSONException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}
	
	public final static void refreshMediaScanner() {
		
	}
	
	public final static void deleteFromMediaStore(Uri uri, Context c) {
		if(uri.getScheme().equals("file")) {
			File file = new File(uri.getPath());
			Uri[] bases = {
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				MediaStore.Images.Media.INTERNAL_CONTENT_URI
			};
			
			for(Uri base : bases) {
				Cursor cursor = c.getContentResolver().query(
						base, 
						new String[] {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA}, 
						MediaStore.Images.Media.DATA + " =?",
						new String[] {uri.getPath()},
						null);
				while(cursor.moveToNext()) {
					long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID));
					c.getContentResolver().delete(ContentUris.withAppendedId(base, id), null, null);
				}
				
				if(file.exists())
					file.delete();
			}
		} else 
			c.getContentResolver().delete(uri, null, null);		
	}
	
	public final static byte[] getBytesFromUri(Uri uri, Context c) {
    	if (uri.getScheme() != null && uri.getScheme().equals("file")) {
    		return getBytesFromFile(new File(uri.toString()));
    	} else {
	    	Cursor imageCursor = c.getContentResolver().query(uri, new String[] {MediaStore.Images.Media.DATA}, null, null, null );
	    	if ( imageCursor != null && imageCursor.getCount() == 1 ) {
		        imageCursor.moveToFirst();
		        return getBytesFromFile(new File(imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA))));
	    	} else
	    		return null;
    	}
	}
}
