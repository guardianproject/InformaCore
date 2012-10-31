package org.witness.informacam.storage;

import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.utils.Constants.Informa.Keys.Genealogy;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Exif;
import org.witness.informacam.utils.Time;

import android.content.ContentUris;
import android.content.Context;
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
		try {
			byte[] b = bytes;
			if(isBase64)
				b = Base64.decode(bytes);
			
			Bitmap bitmap = BitmapFactory.decodeByteArray(b,0,b.length);
			Matrix m = new Matrix();
			m.postScale(80f/bitmap.getWidth(), 80f/bitmap.getHeight());
			
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
		} catch (NullPointerException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		
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
	
	// TODO: THIS WILL HAVE TO BE RESOLVED WITH GPS TIME.
	public final static LogPack getVideoMetadata(Uri uri, String filepath, Context c) {
		try {
			LogPack logPack = new LogPack(Informa.CaptureEvent.Keys.TYPE, Informa.CaptureEvent.METADATA_CAPTURED);
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			retriever.setDataSource(c, uri);
			
			String[] columnsToSelect = { MediaStore.Video.Media.DATE_TAKEN };
	    	Cursor videoCursor = c.getContentResolver().query(uri, columnsToSelect, null, null, null );
	    	if (videoCursor != null && videoCursor.getCount() == 1 ) {
		        videoCursor.moveToFirst();
		        logPack.put(Exif.TIMESTAMP, Time.resolveTimestampWithGPSTime(videoCursor.getString(videoCursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN))));
		        
		        videoCursor.close();
	    	}
	    	
						
			logPack.put(Exif.IMAGE_LENGTH, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
			logPack.put(Exif.IMAGE_WIDTH, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
			logPack.put(Exif.DURATION, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
			
			logPack.put(Data.Description.MEDIA_TYPE, Media.Type.VIDEO);
			logPack.put(Data.Description.ORIGINAL_HASH, MediaHasher.hash(new File(filepath), "SHA-1"));
			logPack.put(Genealogy.LOCAL_MEDIA_PATH, filepath);
			
			Bitmap b = retriever.getFrameAtTime(Math.max(logPack.getLong(Exif.DURATION)/2, 0), MediaMetadataRetriever.OPTION_CLOSEST);
			
			int scale = Math.min(8, logPack.getInt(Exif.IMAGE_WIDTH)/10);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = scale;
			
			logPack.put(Media.Manifest.Keys.THUMBNAIL, new String(Base64.encode(IOUtility.getBytesFromBitmap(b, 20))));
						
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
			
			logPack.put(Exif.TIMESTAMP, Time.resolveTimestampWithGPSTime(ei.getAttribute(Exif.TIMESTAMP)));
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
			
			int scale = Math.min(8, logPack.getInt(Exif.IMAGE_WIDTH)/10);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = scale;
			
			Bitmap b = BitmapFactory.decodeFile(filepath, opts);
			
			logPack.put(Media.Manifest.Keys.THUMBNAIL, new String(Base64.encode(IOUtility.getBytesFromBitmap(b, 20))));
						
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

	public static Object unzip(File ictd) {
		return unzip(ictd, false);
	}
	
	@SuppressWarnings("rawtypes")
	public static Object unzip(File ictd, boolean toIOCipher) {
		Object rootFolder = null;
		
		if(toIOCipher) {
			rootFolder = (info.guardianproject.iocipher.File) IOCipherService.getInstance().getFile(Storage.IOCipher.DUMP_FOLDER + "/" + ictd.getName().replace(".", "_"));
			if(!((info.guardianproject.iocipher.File) rootFolder).exists())
				((info.guardianproject.iocipher.File) rootFolder).mkdir();
		} else {
			rootFolder = (java.io.File) new File(Storage.FileIO.DUMP_FOLDER, ictd.getName().replace(".", "_"));
			if(!((java.io.File) rootFolder).exists())
				((java.io.File) rootFolder).mkdir();
		}
		try {
			ZipFile zipFile = new ZipFile(ictd);
			Enumeration entries = zipFile.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();
				
				boolean isOmitable = false;
				for(String omit : Storage.ICTD.ZIP_OMITABLES) {
					if(entry.getName().contains(omit) || String.valueOf(entry.getName().charAt(0)).compareTo(".") == 0) {
						isOmitable = true;
					}
					
					if(isOmitable)
						break;
				}
				
				if(isOmitable)
					continue;
				
				if(entry.isDirectory()) {
					if(toIOCipher) {
						IOCipherService.getInstance().getFile((info.guardianproject.iocipher.File) rootFolder, entry.getName());
					} else {
						(new java.io.File((java.io.File) rootFolder, entry.getName())).mkdir();
					}
					
					continue;
				}
				
				BufferedOutputStream bos = null;
				if(toIOCipher) {
					info.guardianproject.iocipher.File entryFile = IOCipherService.getInstance().getFile((info.guardianproject.iocipher.File) rootFolder, entry.getName());
					bos = new BufferedOutputStream(new info.guardianproject.iocipher.FileOutputStream(entryFile));
				} else {
					java.io.File entryFile = new java.io.File((java.io.File) rootFolder, entry.getName());
					bos = new BufferedOutputStream(new java.io.FileOutputStream(entryFile));
				}
				
				InputStream is = zipFile.getInputStream(entry);
				
				byte[] buf = new byte[1024];
				int ch;
				while((ch = is.read(buf)) >= 0)
					bos.write(buf, 0, ch);
				
				is.close();
				bos.close();
			}
			
			zipFile.close();
		} catch (ZipException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		}
		
		return rootFolder;
	}
}
