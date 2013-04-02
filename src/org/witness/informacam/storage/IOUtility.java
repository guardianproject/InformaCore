package org.witness.informacam.storage;

import info.guardianproject.iocipher.File;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage;
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

	public static byte[] zipBytes(byte[] bytes, InputStream is, int source) {
		try {
			OutputStream os = null;
			BufferedOutputStream bos = null;
			int buf = 2048;

			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
			ZipEntry ze;

			while((ze = zis.getNextEntry()) != null) {
				int count;
				byte[] data = new byte[buf];

				switch(source) {
				case Type.IOCIPHER:
					os = new info.guardianproject.iocipher.FileOutputStream(ze.getName());
					break;
				}
				
				bos = new BufferedOutputStream(os, buf);
				while((count = zis.read(data, 0, buf)) != -1) {
					bos.write(data, 0, count);
				}
				
				bos.flush();
				bos.close();
			}
			
			byte[] zip = new byte[zis.available()];
			zis.read(zip);
			zis.close();
			
			return zip;
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;

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

	@SuppressWarnings("rawtypes")
	public static List<String> unzipFile (byte[] rawContent, String root, int destination) {
		IOService ioService = InformaCam.getInstance().ioService;
		List<String> paths = new ArrayList<String>();

		ZipFile zipFile = null;
		String rootFolderPath = "";

		switch(destination) {
		case Type.IOCIPHER:
			info.guardianproject.iocipher.File zf;
			if(root != null) {
				info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(root);
				if(!rootFolder.exists()) {
					rootFolder.mkdir();
				}

				zf = new info.guardianproject.iocipher.File(rootFolder, System.currentTimeMillis() + ".zip");
				rootFolderPath = rootFolder.getAbsolutePath();
			} else {
				zf = new info.guardianproject.iocipher.File(System.currentTimeMillis() + ".zip");
			}

			ioService.saveBlob(rawContent, zf);
			try {
				zipFile = new ZipFile(zf);
			} catch (ZipException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}


			break;
		}

		if(zipFile == null) {
			return null;
		}

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
				switch(destination) {
				case Type.IOCIPHER:
					info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(entry.getName());
					if(!rootFolder.exists()) {
						rootFolder.mkdir();
					}

					rootFolderPath = rootFolder.getAbsolutePath();
					break;
				}

				continue;
			}

			BufferedOutputStream bos = null;
			try {
				switch(destination) {
				case Type.IOCIPHER:
					info.guardianproject.iocipher.File entryFile = new info.guardianproject.iocipher.File(rootFolderPath, entry.getName());
					bos = new BufferedOutputStream(new info.guardianproject.iocipher.FileOutputStream(entryFile));
					paths.add(entryFile.getAbsolutePath());
					break;
				}

				InputStream is = zipFile.getInputStream(entry);
				byte[] buf = new byte[1024];
				int ch;
				while((ch = is.read(buf)) >= 0) {
					bos.write(buf, 0, ch);
				}

				is.close();
				bos.close();

			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return null;
			}
		}

		try {
			zipFile.close();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		}

		return paths;
	}
}
