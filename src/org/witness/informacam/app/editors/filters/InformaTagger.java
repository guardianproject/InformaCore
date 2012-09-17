package org.witness.informacam.app.editors.filters;

import java.util.Properties;

import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

public class InformaTagger implements RegionProcesser
{
	Properties mProps;
	private Bitmap mPreview;
	
	public InformaTagger ()
	{
		mProps = new Properties ();
		mProps.put(ImageRegion.Subject.PSEUDONYM, "");
		mProps.put(ImageRegion.Subject.INFORMED_CONSENT_GIVEN, "false");
		mProps.put(ImageRegion.Subject.PERSIST_FILTER, "false");
		mProps.put(ImageRegion.FILTER, this.getClass().getName());
	}
	
	public InformaTagger(String pseudonym, boolean informedConsentGiven, boolean persistFilter) {
		mProps = new Properties ();
		mProps.put(ImageRegion.Subject.PSEUDONYM, pseudonym);
		mProps.put(ImageRegion.Subject.INFORMED_CONSENT_GIVEN, String.valueOf(informedConsentGiven));
		mProps.put(ImageRegion.Subject.PERSIST_FILTER, String.valueOf(persistFilter));
		mProps.put(ImageRegion.FILTER, this.getClass().getName());
	}
	
	@Override
	public void processRegion (RectF rect, Canvas canvas,  Bitmap bitmap) 
	{
		// return properties and data as a map
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

	public Properties getProperties()
	{
		return mProps;
	}
	
	public void setProperties(Properties props)
	{
		mProps = props;
	}

	@Override
	public Bitmap getBitmap() {
		return mPreview;
	}
}
