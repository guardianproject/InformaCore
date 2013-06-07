package org.witness.informacam.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class ImageUtility {
	private final static String LOG = App.LOG;
	
	public static int getOrientation(Bitmap b) {
		return b.getWidth() > b.getHeight() ? Codes.Media.ORIENTATION_LANDSCAPE : Codes.Media.ORIENTATION_PORTRAIT;
	}
	
	public static Bitmap getVideoFrame(Context context, java.io.File source, int[] dims) {
		try {
			VideoConstructor videoConstructor = new VideoConstructor(context);
			return videoConstructor.getAFrame(source, dims);
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static Bitmap drawableToBitmap(Drawable d) {
		if(d instanceof BitmapDrawable) {
			return ((BitmapDrawable) d).getBitmap();
		}
		
		int w = d.getIntrinsicWidth();
		w = w > 0 ? w : 1;
		
		int h = d.getIntrinsicHeight();
		h = h > 0 ? h : 1;
		
		Bitmap b = Bitmap.createBitmap(w, h, Config.ARGB_8888);
		Canvas c = new Canvas(b);
		d.setBounds(0, 0, c.getWidth(), c.getHeight());
		d.draw(c);
		
		return b;
	}
	
	public static byte[] downsampleImage(float scaleW, float scaleH, Bitmap source) {
		Matrix matrix = new Matrix();
		matrix.postScale(scaleW, scaleH);

		Bitmap bitmap = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(CompressFormat.JPEG, 60, baos);
		bitmap.recycle();
		
		try {
			baos.flush();
			baos.close();
			return baos.toByteArray();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static byte[] downsampleImageForListOrPreview(Bitmap source) {
		return downsampleImageForListOrPreview(source, 640, 480);
	}
	
	public static byte[] downsampleImageForListOrPreview(Bitmap source, int maximumX, int maximumY) {
		float scaleW = ((float) maximumX)/source.getWidth();
		float scaleH = ((float) maximumY)/source.getHeight();
		
		return downsampleImage(scaleW, scaleH, source);
	}

	public static Bitmap createThumb(Bitmap source, int[] dims) {
		Log.d(LOG, "btw, dims: " + dims[0] + "x" + dims[1]);
		
		float scaleW = 96f/dims[0];
		float scaleH = 96f/dims[1];

		Matrix matrix = new Matrix();
		matrix.postScale(scaleW, scaleH);

		try {
			return Bitmap.createBitmap(source, 0, 0, dims[0], dims[1], matrix, false);
		} catch(IllegalArgumentException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			
			return Bitmap.createBitmap(source, 0, 0, dims[1], dims[0], matrix, false);
		}
	}
}
