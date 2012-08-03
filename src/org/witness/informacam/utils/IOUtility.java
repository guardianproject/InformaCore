package org.witness.informacam.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.bouncycastle.util.encoders.Base64;
import org.witness.informacam.utils.Constants.App;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		
		
		return bytes;
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
