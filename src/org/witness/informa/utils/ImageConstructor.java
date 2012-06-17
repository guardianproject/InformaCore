package org.witness.informa.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;


import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.Informa.Image;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Keys.Genealogy;
import org.witness.informa.utils.InformaConstants.Keys.ImageRegion;
import org.witness.informa.utils.InformaConstants.Keys.Informa;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.OriginalImageHandling;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.MediaHasher;
import org.witness.ssc.image.filters.CrowdPixelizeObscure;
import org.witness.ssc.image.filters.InformaTagger;
import org.witness.ssc.image.filters.PixelizeObscure;
import org.witness.ssc.image.filters.SolidObscure;
import org.witness.ssc.utils.ObscuraConstants;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.util.Base64;
import android.util.Log;

public class ImageConstructor {
	public static native int constructImage(
			String originalImageFilename, 
			String informaImageFilename, 
			String metadataObjectString, 
			int metadataLength);
	public native byte[] redactRegion(
			String originalImageFilename,
			String informaImageFilename,
			int left,
			int right,
			int top,
			int bottom,
			String redactionCommand);
	
	private JSONArray imageRegions;
	private ArrayList<ContentValues> unredactedRegions;
	JSONObject metadataObject;
	File clone;
	String base, unredactedHash, redactedHash;
	String containmentArray = InformaConstants.NOT_INCLUDED;
	
	Context c;
	private DatabaseHelper dh;
	private SQLiteDatabase db;
	Apg apg;
	private SharedPreferences _sp;	
	public ArrayList<Map<Long, String>> metadataForEncryption;
		
	static {
		System.loadLibrary("JpegRedaction");
	}
	
	public ImageConstructor(Context c, String metadataObjectString, String baseName) throws JSONException, NoSuchAlgorithmException, IOException {
		this.c = c;
		
		metadataObject = (JSONObject) new JSONTokener(metadataObjectString).nextValue();
		unredactedRegions = new ArrayList<ContentValues>();
		clone = new File(InformaConstants.DUMP_FOLDER, ObscuraConstants.TMP_FILE_NAME_IMAGE);
		
		_sp = PreferenceManager.getDefaultSharedPreferences(c);
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(_sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		unredactedHash = getBitmapHash(clone);
		
		base = baseName.split("_")[0] + ".jpg";
		
		metadataForEncryption = new ArrayList<Map<Long, String>>();
		
		// do redaction
		this.imageRegions = (JSONArray) (metadataObject.getJSONObject(Keys.Informa.DATA)).getJSONArray(Keys.Data.IMAGE_REGIONS);
		for(int i=0; i<imageRegions.length(); i++) {
			JSONObject ir = imageRegions.getJSONObject(i);
			
			String redactionMethod = ir.getString(Keys.ImageRegion.FILTER);
			if(!redactionMethod.equals(InformaTagger.class.getName())) {
				
				JSONObject dimensions = ir.getJSONObject(Keys.ImageRegion.DIMENSIONS);
				JSONObject coords = ir.getJSONObject(Keys.ImageRegion.COORDINATES);
				
				int top = (int) (coords.getInt(Keys.ImageRegion.TOP));
				int left = (int) (coords.getInt(Keys.ImageRegion.LEFT));
				int right = (int) (left + dimensions.getInt(Keys.ImageRegion.WIDTH));
				int bottom = (int) (top + dimensions.getInt(Keys.ImageRegion.HEIGHT));
				
				String redactionCode = "";
				if(redactionMethod.equals(PixelizeObscure.class.getName()))
					redactionCode = ObscuraConstants.Filters.PIXELIZE;
				else if(redactionMethod.equals(SolidObscure.class.getName()))
					redactionCode = ObscuraConstants.Filters.SOLID;
				else if(redactionMethod.equals(CrowdPixelizeObscure.class.getName()))
					redactionCode = ObscuraConstants.Filters.CROWD_PIXELIZE;
				
				byte[] redactionPack = redactRegion(clone.getAbsolutePath(), clone.getAbsolutePath(), left, right, top, bottom, redactionCode);
				
				// insert hash and data length into metadata package
				JSONObject irData = new JSONObject();
				irData.put(ImageRegion.Data.UNREDACTED_HASH, MediaHasher.hash(redactionPack, "SHA-1"));
				irData.put(ImageRegion.Data.LENGTH, redactionPack.length);
				irData.put(ImageRegion.Data.BYTES, bytesToString(Base64.encode(redactionPack, Base64.NO_WRAP)));
				//irData.put(ImageRegion.Data.POSITION, appendImageRegion(redactionPack));
				
				ir.put(Keys.ImageRegion.UNREDACTED_DATA, irData);
				
				//zip data for database
				ContentValues rcv = new ContentValues();
				rcv.put(ImageRegion.Data.UNREDACTED_HASH, MediaHasher.hash(redactionPack, "SHA-1"));
				rcv.put(ImageRegion.DATA, gzipBytes(Base64.encode(redactionPack, Base64.NO_WRAP)));
				rcv.put(ImageRegion.BASE, base);
				unredactedRegions.add(rcv);
			}
		}
		
		redactedHash = getBitmapHash(clone);
		dh.setTable(db, Tables.IMAGE_REGIONS);
		for(ContentValues rcv : unredactedRegions)
			db.insert(dh.getTable(), null, rcv);
	}

	public void doCleanup() {
		//TODO: handle original image, remove tmp.jpg, camtmp.jpg
	}
	
	private String getBitmapHash(File file) throws NoSuchAlgorithmException, IOException {
		Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
		// FYI, getByteCount is not yet available for all devices (API 12)
		String hash = "";
		ByteBuffer buf;
		
		try {
			buf = ByteBuffer.allocate(bitmap.getByteCount());		
		} catch(NoSuchMethodError e) {
			buf = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
		}
		
		bitmap.copyPixelsToBuffer(buf);
		hash = MediaHasher.hash(buf.array(), "SHA-1");
		buf.clear();
		buf = null;
		return hash;
	}
	
	public void createVersionForTrustedDestination(Image i) throws JSONException, NoSuchAlgorithmException, IOException {
		
		// insert the unredacted and redacted media hashes
		JSONObject mediaHash = new JSONObject();
		mediaHash.put(Keys.Image.UNREDACTED_IMAGE_HASH, unredactedHash);
		mediaHash.put(Keys.Image.REDACTED_IMAGE_HASH, redactedHash);
		metadataObject.getJSONObject(Keys.Informa.DATA).put(Keys.Data.MEDIA_HASH, mediaHash);
		
		// replace the metadata's intended destination
		metadataObject.getJSONObject(Keys.Informa.INTENT).put(Keys.Intent.INTENDED_DESTINATION, i.getIntendedDestination());
		
		// insert into database for later
		ContentValues cv = new ContentValues();
		// package zipped image region bytes
		cv.put(Keys.Image.METADATA, metadataObject.toString());
		cv.put(Keys.Image.REDACTED_IMAGE_HASH, redactedHash);
		cv.put(Keys.Image.LOCATION_OF_ORIGINAL, ((JSONObject) metadataObject.getJSONObject(Informa.GENEALOGY)).getString(Keys.Image.LOCAL_MEDIA_PATH));
		cv.put(Keys.Image.LOCATION_OF_OBSCURED_VERSION, i.getAbsolutePath());
		cv.put(Keys.Image.TRUSTED_DESTINATION, i.getIntendedDestination());
		cv.put(Keys.Image.CONTAINMENT_ARRAY, containmentArray);
		cv.put(Keys.Image.UNREDACTED_IMAGE_HASH, unredactedHash);
		cv.put(Keys.Media.MEDIA_TYPE, MediaTypes.PHOTO);
		
		dh.setTable(db, Tables.IMAGES);
		Map<Long, String> mo = new HashMap<Long, String>();
		mo.put(db.insert(dh.getTable(), null, cv), i.getAbsolutePath());
		metadataForEncryption.add(mo);		
	}
	
	public static long constructImage(MetadataPack metadataPack) throws JSONException {
		// insert metadata: filename, metadata package
		JSONObject metadata = (JSONObject) new JSONTokener(metadataPack.metadata).nextValue();
		if(constructImage(metadataPack.clonepath, metadataPack.filepath, metadataPack.metadata, metadataPack.metadata.length()) > 0)
			return ((JSONObject) metadata.getJSONObject(Informa.GENEALOGY)).getLong(Keys.Genealogy.DATE_CREATED);
		else
			return 0L;
	}
	
	private long appendImageRegion(byte[] irData) throws IOException {
		FileOutputStream fos = new FileOutputStream(clone, true);
		fos.write(irData);
		fos.close();
		return (clone.length() - irData.length);
	}
	
	private int handleOriginalImage() {
		switch(Integer.parseInt(_sp.getString(Keys.Settings.DEFAULT_IMAGE_HANDLING,""))) {
		case OriginalImageHandling.ENCRYPT_ORIGINAL:
			ExecutorService ex = Executors.newFixedThreadPool(100);
			Future<Integer> f = ex.submit(new Callable<Integer>() {
				public Integer call() {
					try {
						return encryptOriginal();
					} catch (IOException e) {
						return 0;
					}
				}
			});
			try {
				return f.get();
			} catch (InterruptedException e) {
				Log.d(InformaConstants.TAG, "Error encrypting original : " + e.toString());
				return 0;
			} catch (ExecutionException e) {
				Log.d(InformaConstants.TAG, "Error encrypting original : " + e.toString());
				return 0;
			}
		case OriginalImageHandling.DELETE_ORIGINAL:
			new Thread(new Runnable() {
				@Override
				public void run() {
					deleteOriginal();
				}
			}).start();
			return 1;
		default:
			return 0;
		}
	}
	
	private void deleteOriginal() {
		// TODO: copy over blank picture, run media scanner, delete file
		Log.d(InformaConstants.TAG, "deleting original");
		SimpleDateFormat sdf = new SimpleDateFormat(ObscuraConstants.EXPORT_DATE_FORMAT);
		String dateString = "";
		Date date = new Date(0);
		try {
			JSONObject g = metadataObject.getJSONObject(Informa.GENEALOGY);
			date.setTime(g.getLong(Genealogy.DATE_CREATED));
		} catch(JSONException e) {}
		catch(NullPointerException e) {}
		
		dateString = sdf.format(date);
		
		ContentValues cv = new ContentValues();
		cv.put(Images.Media.DATE_ADDED, dateString);
		cv.put(Images.Media.DATE_TAKEN, dateString);
		cv.put(Images.Media.DATE_MODIFIED, dateString);
		cv.put(Images.Media.TITLE, dateString);
		
		Uri savedImageUri = c.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, cv);
		
		try {
			copy(Uri.fromFile(clone), savedImageUri);
			MediaScannerConnection.scanFile(
					c,
					new String[] {pullPathFromUri(savedImageUri).getAbsolutePath()},
					new String[] {ObscuraConstants.MIME_TYPE_JPEG},
					null);
		} catch (IOException e) {}
		
	}
	
	private int encryptOriginal() throws IOException {
		// TODO: encypt to device
		return 1;
		
	}
	
	private byte[] gzipBytes(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gos = new GZIPOutputStream(baos);
		gos.write(data);
		gos.close();
		baos.close();
		return baos.toByteArray();
	}
	
	private String bytesToString(byte[] data) {
		StringBuffer sb = new StringBuffer();
		for(byte b : data) {
			sb.append(Integer.toHexString(0xFF & b));
		}
		return sb.toString();
	}
	
	private File stringToFile(String data, String dir, String filename) {
		File file = new File(dir, filename);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			out.write(data);
			out.close();
			return file;
		} catch(IOException e) {
			return null;
		}
		
	}
	
	public static File bytesToFile(byte[] data, String filename) throws IOException {
		File byteFile = new File(InformaConstants.DUMP_FOLDER, filename);
		FileOutputStream fos = new FileOutputStream(byteFile);
		fos.write(data);
		fos.close();
		return byteFile;
	}
	
	public static byte[] fileToBytes(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		byte[] fileBytes = new byte[(int) file.length()];
		
		int offset = 0;
		int bytesRead = 0;
		while(offset < fileBytes.length && (bytesRead = fis.read(fileBytes, offset, fileBytes.length - offset)) >= 0)
			offset += bytesRead;
		fis.close();
		return fileBytes;
	}
	
	public File pullPathFromUri(Uri originalUri) {
		String originalImageFilePath = null;
		if (originalUri.getScheme() != null && originalUri.getScheme().equals("file")) 
			originalImageFilePath = originalUri.toString();
	    	
	    else {
	    	String[] columnsToSelect = { Images.Media.DATA };
	    	Cursor imageCursor = c.getContentResolver().query(originalUri, columnsToSelect, null, null, null );
		    if (imageCursor != null && imageCursor.getCount() == 1 ) {
		    	imageCursor.moveToFirst();
			    originalImageFilePath = imageCursor.getString(imageCursor.getColumnIndex(Images.Media.DATA));
		    }
	    }

		return new File(originalImageFilePath);
	}
	
    private void copy (Uri uriSrc, File fileTarget) throws IOException
    {
    	InputStream is = c.getContentResolver().openInputStream(uriSrc);
		
		OutputStream os = new FileOutputStream (fileTarget);
			
		copyStreams (is, os);
    	
    }
    
    private void copy (Uri uriSrc, Uri uriTarget) throws IOException
    {
    	
    	InputStream is = c.getContentResolver().openInputStream(uriSrc);
		
		OutputStream os = c.getContentResolver().openOutputStream(uriTarget);
			
		copyStreams (is, os);

    	
    }
    
    public static void copyStreams(InputStream input, OutputStream output) throws IOException {
        // if both are file streams, use channel IO
        if ((output instanceof FileOutputStream) && (input instanceof FileInputStream)) {
          try {
            FileChannel target = ((FileOutputStream) output).getChannel();
            FileChannel source = ((FileInputStream) input).getChannel();

            source.transferTo(0, Integer.MAX_VALUE, target);

            source.close();
            target.close();

            return;
          } catch (Exception e) { /* failover to byte stream version */
          }
        }

        byte[] buf = new byte[8192];
        while (true) {
          int length = input.read(buf);
          if (length < 0)
            break;
          output.write(buf, 0, length);
        }

        try {
          input.close();
        } catch (IOException ignore) {
        }
        try {
          output.close();
        } catch (IOException ignore) {
        }
      }
	
}
