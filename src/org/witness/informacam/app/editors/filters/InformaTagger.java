package org.witness.informacam.app.editors.filters;

import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.utils.FormUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Forms;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Log;

public class InformaTagger implements RegionProcesser
{
	Properties mProps;
	private Bitmap mPreview;
	
	public InformaTagger(int obscureType) throws JSONException {
		JSONObject form = FormUtility.getAnnotationPlugins(obscureType).get(obscureType);

		mProps = new Properties ();
		mProps.put(ImageRegion.Subject.FORM_NAMESPACE, form.getString(Forms.TITLE));
		mProps.put(ImageRegion.Subject.FORM_DEF_PATH, form.getString(Forms.DEF));
		mProps.put(ImageRegion.FILTER, this.getClass().getName());
	}
	
	public InformaTagger(String formNamespace, String formDefPath) {
		this(formNamespace, formDefPath, null);
	}
	
	public InformaTagger(String formNamespace, String formDefPath, String formData) {
		mProps = new Properties ();
		mProps.put(ImageRegion.Subject.FORM_NAMESPACE, formNamespace);
		mProps.put(ImageRegion.Subject.FORM_DEF_PATH, formDefPath);
		mProps.put(ImageRegion.FILTER, this.getClass().getName());
		
		if(formData != null)
			mProps.put(ImageRegion.Subject.FORM_DATA, formData);
		
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
