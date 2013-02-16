package org.witness.informacam.app.editors.image;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.editors.filters.CrowdPixelizeObscure;
import org.witness.informacam.app.editors.filters.Filter;
import org.witness.informacam.app.editors.filters.InformaTagger;
import org.witness.informacam.app.editors.filters.PixelizeObscure;
import org.witness.informacam.app.editors.filters.RegionProcesser;
import org.witness.informacam.app.editors.filters.SolidObscure;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.utils.FormUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Forms;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ImageRegion implements OnActionItemClickListener 
{

	public static final String LOGTAG = "SSC.ImageRegion";

	// Rect for this when unscaled
	public RectF mBounds;
	//public RectF mTmpBounds;

	// Start point for touch events
	PointF mStartPoint = null;
	PointF mNonMappedStartPoint = null;

	// Our current mode
	public static final int NORMAL_MODE = 0;
	public static final int EDIT_MODE = 1;

	// The current touch event mode
	public final static int NONE = 0;
	public final static int MOVE = 1;


	// What should be done to this region
	public static final int NOTHING = 0;
	public static final int OBSCURE = 1;
	
	boolean selected = false;

	public static final int CORNER_UPPER_LEFT = 1;
	public static final int CORNER_LOWER_LEFT = 2;
	public static final int CORNER_UPPER_RIGHT = 3;
	public static final int CORNER_LOWER_RIGHT = 4;
	public static final int CORNER_NONE = -1;

	/* Add each ObscureMethod to this list and update the 
	 * createObscuredBitmap method in ImageEditor
	 */
	List<Filter> mFilters;
	int mObscureType = -1;

	//the other of these strings and resources determines the order in the popup menu

	public final Drawable unidentifiedBorder, identifiedBorder;
	public Drawable imageRegionBorder;

	// The ImageEditor object that contains us
	ImageEditor mImageEditor;

	// Popup menu for region 
	// What's the license for this?
	QuickAction mPopupMenu;

	ActionItem[] mActionFilters;

	RegionProcesser mRProc;

	private final static float MIN_MOVE = 5f;

	private final static float CORNER_MAX = 50f;

	private int cornerMode = -1;
	private long timestampOnGeneration;

	public interface ImageRegionListener {
		public void onImageRegionCreated(ImageRegion ir);
		public void onImageRegionChanged(ImageRegion ir);
		public void onImageRegionRemoved(ImageRegion ir);
	}

	public RegionProcesser getRegionProcessor() {
		return mRProc;
	}

	public void setRegionProcessor(RegionProcesser rProc) {
		mRProc = rProc;
		Properties prop = mRProc.getProperties();
		prop.put(Data.ImageRegion.TIMESTAMP, timestampOnGeneration);
		mRProc.setProperties(prop);
	}

	public JSONObject getRepresentation() throws JSONException {
		JSONObject representation = new JSONObject();
		Enumeration<?> e = mRProc.getProperties().propertyNames();

		while(e.hasMoreElements()) {
			String prop = (String) e.nextElement();
			representation.put(prop, mRProc.getProperties().get(prop));
		}

		return representation;
	}

	public void setCornerMode (float x, float y)
	{
		float[] points = {x,y};        	
		iMatrix.mapPoints(points);

		float cSize = CORNER_MAX;

		cSize = iMatrix.mapRadius(cSize);

		if (Math.abs(mBounds.left-points[0])<cSize
				&& Math.abs(mBounds.top-points[1])<cSize
				)
		{
			cornerMode = CORNER_UPPER_LEFT;
			return;
		}
		else if (Math.abs(mBounds.left-points[0])<cSize
				&& Math.abs(mBounds.bottom-points[1])<cSize
				)
		{
			cornerMode = CORNER_LOWER_LEFT;
			return;
		}
		else if (Math.abs(mBounds.right-points[0])<cSize
				&& Math.abs(mBounds.top-points[1])<cSize
				)
		{
			cornerMode = CORNER_UPPER_RIGHT;
			return;
		}
		else if (Math.abs(mBounds.right-points[0])<cSize
				&& Math.abs(mBounds.bottom-points[1])<cSize
				)
		{
			cornerMode = CORNER_LOWER_RIGHT;
			return;
		}

		cornerMode = CORNER_NONE;
	}


	/* For touch events, whether or not to show the menu
	 */
	boolean moved = false;

	Matrix mMatrix, iMatrix;

	int fingerCount = 0;

	public ImageRegion(
			ImageEditor imageEditor, 
			float left, float top, 
			float right, float bottom, Matrix matrix) {
		this(imageEditor, left, top, right, bottom, matrix, null, false, InformaService.getInstance().getCurrentTime());
	}


	public ImageRegion(
			ImageEditor imageEditor, 
			float left, float top, 
			float right, float bottom, Matrix matrix, RegionProcesser rp, boolean fromCache, long timestamp) 
	{
		//super(imageEditor);
		super();

		// Set the mImageEditor that this region belongs to to the one passed in
		mImageEditor = imageEditor;
		// set the borders for tags in Non-Edit mode
		identifiedBorder = imageEditor.getResources().getDrawable(R.drawable.border_idtag);
		unidentifiedBorder = imageEditor.getResources().getDrawable(R.drawable.border);

		mMatrix = matrix;
		iMatrix = new Matrix();
		mMatrix.invert(iMatrix);

		mBounds = new RectF(left, top, right, bottom);

		timestampOnGeneration = timestamp;
		
		
		// init the plugins we have
		mFilters = new ArrayList<Filter>();
		for(Filter filter : App.ImageEditor.INFORMA_CAM_PLUGINS) {
			if(filter.is_available)
				mFilters.add(filter);
		}
		
		Map<Integer, JSONObject> plugins = FormUtility.getAnnotationPlugins(mFilters.size());
		Iterator<Entry<Integer, JSONObject>> pIt = plugins.entrySet().iterator();
		
		
		while(pIt.hasNext()) {
			Entry<Integer, JSONObject> plugin = pIt.next();
			try {
				mFilters.add(new Filter(plugin.getValue().getString(Forms.TITLE), R.drawable.ic_file_blue, InformaTagger.class, null));
			} catch (JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		
		//set default processor
		if(rp == null)
			updateRegionProcessor(mFilters.get(0));
		else
			this.setRegionProcessor(rp);

		// notify informa
		if(!fromCache)
			InformaService.getInstance().onImageRegionCreated(this);
	}

	public void setMatrix (Matrix matrix)
	{
		mMatrix = matrix;
		iMatrix = new Matrix();
		mMatrix.invert(iMatrix);
	}

	public void inflatePopup(boolean showDelayed) {

		if (mPopupMenu == null)
			initPopup();


		if (showDelayed) {
			// We need layout to pass again, let's wait a second or two
			new Handler() {
				@Override
				public void handleMessage(Message msg) {

					//float[] points = {mBounds.centerX(), mBounds.centerY()};		
					//mMatrix.mapPoints(points);


					//mPopupMenu.show(mImageEditor.getImageView(), (int)points[0], (int)points[1]);
					mPopupMenu.show(mImageEditor.getImageView(), (int)mBounds.centerX(), (int)mBounds.centerY());
				}
			}.sendMessageDelayed(new Message(), 300);
		} else {			

			float[] points = {mBounds.centerX(), mBounds.centerY()};		
			mMatrix.mapPoints(points);

			//mPopupMenu.show(mImageEditor.getImageView(), (int)points[0], (int)points[1]);
			mPopupMenu.show(mImageEditor.getImageView(), (int)mBounds.centerX(), (int)mBounds.centerY());
		}


	}

	private void initPopup ()
	{
		mPopupMenu = new QuickAction(mImageEditor);

		ActionItem aItem;

		for (int i = 0; i < mFilters.size(); i++)
		{

			aItem = new ActionItem();
			aItem.setTitle(mFilters.get(i).label);

			aItem.setIcon(mImageEditor.getResources().getDrawable(mFilters.get(i).resId));			

			mPopupMenu.addActionItem(aItem);

		}

		aItem = new ActionItem();
		aItem.setTitle("Clear Tag");
		aItem.setIcon(mImageEditor.getResources().getDrawable(R.drawable.ic_context_delete));

		mPopupMenu.addActionItem(aItem);

		mPopupMenu.setOnActionItemClickListener(this);
	}


	boolean isSelected ()
	{
		return selected;
	}

	void setSelected (boolean _selected)
	{
		selected = _selected;
	}


	private void updateBounds(float left, float top, float right, float bottom) 
	{
		//Log.i(LOGTAG, "updateBounds: " + left + "," + top + "," + right + "," + bottom);
		mBounds.set(left, top, right, bottom);

		//updateLayout();
	}

	float scaleX, scaleY, leftOffset, topOffset;

	public void updateMatrix ()
	{
		float[] mValues = new float[9];
		mMatrix.getValues(mValues);		
		mMatrix.invert(iMatrix);
		scaleX = mValues[Matrix.MSCALE_X];
		scaleY = mValues[Matrix.MSCALE_Y];

		leftOffset = mValues[Matrix.MTRANS_X];
		topOffset = mValues[Matrix.MTRANS_Y];

	}

	public RectF getBounds ()
	{
		return mBounds;
	}


	public boolean onTouch(View v, MotionEvent event) 
	{

		fingerCount = event.getPointerCount();
		//	Log.v(LOGTAG,"onTouch: fingers=" + fingerCount);

		switch (event.getAction() & MotionEvent.ACTION_MASK) {

		case MotionEvent.ACTION_DOWN:

			mImageEditor.doRealtimePreview = true;
			mImageEditor.updateDisplayImage();
			//mTmpBounds = new RectF(mBounds);

			if (fingerCount == 1)
			{
				//float[] points = {event.getX(), event.getY()};                	
				//iMatrix.mapPoints(points);
				//mStartPoint = new PointF(points[0],points[1]);
				mStartPoint = new PointF(event.getX(),event.getY());
				//Log.v(LOGTAG,"startPoint: " + mStartPoint.x + " " + mStartPoint.y);
			}

			moved = false;

			return false;
		case MotionEvent.ACTION_POINTER_UP:
			return moved;

		case MotionEvent.ACTION_UP:

			mImageEditor.doRealtimePreview = true;
			mImageEditor.updateDisplayImage();
			//mTmpBounds = null;

			return moved;

		case MotionEvent.ACTION_MOVE:


			if (fingerCount > 1)
			{

				float[] points = {event.getX(0), event.getY(0), event.getX(1), event.getY(1)};                	
				iMatrix.mapPoints(points);

				mStartPoint = new PointF(points[0],points[1]);

				RectF newBox = new RectF();
				newBox.left = Math.min(points[0],points[2]);
				newBox.top = Math.min(points[1],points[3]);
				newBox.right = Math.max(points[0],points[2]);
				newBox.bottom = Math.max(points[1],points[3]);

				moved = true;

				if (newBox.left != newBox.right && newBox.top != newBox.bottom)
				{

					updateBounds(newBox.left, newBox.top, newBox.right, newBox.bottom);
				}


			}
			else if (fingerCount == 1)
			{


				if (Math.abs(mStartPoint.x- event.getX()) > MIN_MOVE)
				{
					moved = true;

					float[] points = {mStartPoint.x, mStartPoint.y, event.getX(), event.getY()};

					iMatrix.mapPoints(points);

					float diffX = points[0]-points[2];
					float diffY = points[1]-points[3];

					float left = 0, top = 0, right = 0, bottom = 0;

					if (cornerMode == CORNER_NONE)
					{

						left = mBounds.left-diffX;
						top = mBounds.top-diffY;
						right = mBounds.right-diffX;
						bottom = mBounds.bottom-diffY;
					}
					else if (cornerMode == CORNER_UPPER_LEFT)
					{
						left = mBounds.left-diffX;
						top = mBounds.top-diffY;
						right = mBounds.right;
						bottom = mBounds.bottom;

					}
					else if (cornerMode == CORNER_LOWER_LEFT)
					{
						left = mBounds.left-diffX;
						top = mBounds.top;
						right = mBounds.right;
						bottom = mBounds.bottom-diffY;

					}
					else if (cornerMode == CORNER_UPPER_RIGHT)
					{
						left = mBounds.left;
						top = mBounds.top-diffY;
						right = mBounds.right-diffX;
						bottom = mBounds.bottom;
					}
					else if (cornerMode == CORNER_LOWER_RIGHT)
					{
						left = mBounds.left;
						top = mBounds.top;
						right = mBounds.right-diffX;
						bottom = mBounds.bottom-diffY;
					}


					if ((left+CORNER_MAX) > right || (top+CORNER_MAX) > bottom)
						return false;


					//updateBounds(Math.min(left, right), Math.min(top,bottom), Math.max(left, right), Math.max(top, bottom));
					updateBounds(left, top, right, bottom);

					mStartPoint = new PointF(event.getX(),event.getY());
				}
				else
				{
					moved = false;
				}

			}

			mImageEditor.updateDisplayImage();

			return true;

		}

		return false;

	}

	public void updateRegionProcessor (Filter filter) {
		
		try {
			Class<?> rp = Class.forName(filter.regionProcessorClass.getName());
			
			if(!filter.regionProcessorClass.getName().equals(InformaTagger.class.getName())) {
				setRegionProcessor((RegionProcesser) rp.newInstance());
				imageRegionBorder = unidentifiedBorder;
				
				mImageEditor.updateDisplayImage();
			} else {
				// init with ARGS
				if(!(getRegionProcessor() instanceof InformaTagger))
					setRegionProcessor((RegionProcesser) rp.getDeclaredConstructor(int.class).newInstance(mFilters.indexOf(filter)));
				
				imageRegionBorder = identifiedBorder;
				mImageEditor.launchAnnotationActivity(this);
			}

			
		} catch (ClassNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (InstantiationException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}		

		mImageEditor.updateDisplayImage();
		InformaService.getInstance().onImageRegionChanged(this);
	}

	@Override
	public void onItemClick(QuickAction source, int pos, int actionId) {

		if (pos == mFilters.size()) //meaing after the last one
		{
			mImageEditor.deleteRegion(ImageRegion.this);
		}
		else
		{
			mObscureType = pos;
			updateRegionProcessor(mFilters.get(pos));


		}

		mImageEditor.updateDisplayImage();

	}



}
