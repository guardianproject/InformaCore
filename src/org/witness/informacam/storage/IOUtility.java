package org.witness.informacam.storage;

import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.utils.Constants.Informa.Keys.Genealogy;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent.Keys;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Description;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Exif;
import org.witness.informacam.utils.Constants.Media.Type;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
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
		try {
			java.io.FileInputStream fis = new java.io.FileInputStream(file);
			byte[] bytes = new byte[fis.available()];
			fis.read(bytes, 0, fis.available());
			return bytes;
		} catch (FileNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}
	
	public final static byte[] zipFile(info.guardianproject.iocipher.File file) {
		try {
			FileInputStream fis = new FileInputStream(file);
			
			BufferedOutputStream bos = null;
			int buf = 2048;
			
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
			ZipEntry ze;
			
			while((ze = zis.getNextEntry()) != null) {
				int count;
				byte[] data = new byte[buf];
				
				FileOutputStream fos = new FileOutputStream(ze.getName());
				bos = new BufferedOutputStream(fos, buf);
				
				while((count = zis.read(data, 0, buf)) != -1)
					bos.write(data, 0, count);
				
				bos.flush();
				bos.close();
			}
			
			byte[] zip = new byte[zis.available()];
			zis.read(zip);
			zis.close();
			
			return zip;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public final static byte[] getBytesFromFile(info.guardianproject.iocipher.File file) {
		try {
			FileInputStream fis = new FileInputStream(file);
			byte[] bytes = new byte[fis.available()];
			fis.read(bytes, 0, fis.available());
			fis.close();
			return bytes;
		} catch (FileNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}
	
	public final static info.guardianproject.iocipher.File fileFromBytes(byte[] bytes, String fileName) {
		info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(fileName);
		return fileFromBytes(bytes, file);
	}
	
	public final static info.guardianproject.iocipher.File fileFromBytes(byte[] bytes, info.guardianproject.iocipher.File file) {
		
		info.guardianproject.iocipher.FileOutputStream fos;
		try {
			fos = new info.guardianproject.iocipher.FileOutputStream(file);
			fos.write(bytes);
			fos.close();
			return file;
		} catch (FileNotFoundException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
		
	}
	
	public static LogPack getMetadata(Uri mediaCaptureUri, String absolutePath, String mimeType, Context c) {
		if(mimeType.equals(Media.Type.MIME_TYPE_JPEG))
			return getImageMetadata(absolutePath);
		else
			return getVideoMetadata(mediaCaptureUri, absolutePath, c);
	}
	
	public final static LogPack getVideoMetadata(Uri uri, String filepath, Context c) {
		try {
			LogPack logPack = new LogPack(Informa.CaptureEvent.Keys.TYPE, Informa.CaptureEvent.METADATA_CAPTURED);
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			retriever.setDataSource(c, uri);
			
			String[] columnsToSelect = { MediaStore.Video.Media.DATE_TAKEN };
	    	Cursor videoCursor = c.getContentResolver().query(uri, columnsToSelect, null, null, null );
	    	if (videoCursor != null && videoCursor.getCount() == 1 ) {
		        videoCursor.moveToFirst();
		        logPack.put(Exif.TIMESTAMP, videoCursor.getString(videoCursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)));
		        
		        videoCursor.close();
	    	}
	    	
						
			logPack.put(Exif.IMAGE_LENGTH, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
			logPack.put(Exif.IMAGE_WIDTH, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
			logPack.put(Exif.DURATION, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
			
			
			logPack.put(Data.Description.MEDIA_TYPE, Media.Type.VIDEO);
			logPack.put(Data.Description.ORIGINAL_HASH, MediaHasher.hash(new File(filepath), "SHA-1"));
			logPack.put(Genealogy.LOCAL_MEDIA_PATH, filepath);
						
			return logPack;
		} catch (IOException e) {
			return null;
		} catch (JSONException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}
	
	public final static LogPack getImageMetadata(String filepath) {
		try {
			LogPack logPack = new LogPack(Informa.CaptureEvent.Keys.TYPE, Informa.CaptureEvent.METADATA_CAPTURED);
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
			logPack.put(Genealogy.LOCAL_MEDIA_PATH, filepath);
						
			return logPack;
		} catch (IOException e) {
			return null;
		} catch (JSONException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
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
	
	public final static java.io.File getFileFromUri(Uri uri, Context c) {
		if (uri.getScheme() != null && uri.getScheme().equals("file")) {
    		return new java.io.File(uri.toString());
    	} else {
	    	Cursor imageCursor = c.getContentResolver().query(uri, new String[] {MediaStore.Images.Media.DATA}, null, null, null );
	    	if ( imageCursor != null && imageCursor.getCount() == 1 ) {
		        imageCursor.moveToFirst();
		        return new java.io.File(imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA)));
	    	} else
	    		return null;
    	}
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
