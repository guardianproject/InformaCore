package org.witness.informacam.app.editors.filters;

import java.util.Properties;

import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

public class StegoObscure implements RegionProcesser {
	Properties mProps;
	private Bitmap mPreview;
	
	public StegoObscure() {
		this("");
	}
	
	public StegoObscure(String message) {
		mProps = new Properties ();
		mProps.put(ImageRegion.FILTER, this.getClass().getName());
		mProps.put(ImageRegion.Stego.MESSAGE, message);
	}
	
	@Override
	public void processRegion(RectF rect, Canvas canvas, Bitmap bitmap) {
		mProps.put(ImageRegion.COORDINATES, "[" + rect.top + "," + rect.left + "]");
		mProps.put(ImageRegion.WIDTH, Integer.toString((int) Math.abs(rect.left - rect.right)));
		mProps.put(ImageRegion.HEIGHT, Integer.toString((int) Math.abs(rect.top - rect.bottom)));		
		
		mPreview = Bitmap.createBitmap(
				bitmap, 
				(int) rect.left, 
				(int) rect.top,
				(int) Math.min(bitmap.getWidth(),(Math.abs(rect.left - rect.right))), 
				(int) Math.min(bitmap.getHeight(), (Math.abs(rect.top - rect.bottom)))
			);
		
	}

	@Override
	public Properties getProperties() {
		return mProps;
	}

	@Override
	public Bitmap getBitmap() {
		return mPreview;
	}

	@Override
	public void setProperties(Properties props) {
		mProps = props;
	}

}
