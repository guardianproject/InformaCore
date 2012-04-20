package org.witness.ssc.video;

import java.util.Enumeration;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.ssc.image.filters.InformaTagger;
import org.witness.ssc.image.filters.PixelizeObscure;
import org.witness.ssc.image.filters.RegionProcesser;
import org.witness.ssc.image.filters.SolidObscure;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.R;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class VideoRegion {

	public static final String LOGTAG = "SSC.VideoRegion";
	
	// Rect for this when unscaled
	public RectF mBounds;
	
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
	
	public static final int REDACT = 0; // PaintSquareObscure
	public static final int PIXELATE = 1; // PixelizeObscure
	public static final int CONSENT = 2; // PixelizeObscure
	
	boolean selected = false;
	
	/* Add each ObscureMethod to this list and update the 
	 * createObscuredBitmap method in ImageEditor
	 */
	int mObscureType = PIXELATE;

	public final Drawable unidentifiedBorder, identifiedBorder;
	public Drawable imageRegionBorder;
	
	// The ImageEditor object that contains us
	VideoEditor mVideoEditor;
	
	// Popup menu for region 
	// What's the license for this?
	public long startTime = 0;
	public long endTime = 0;
	
	public long mediaDuration = 0;
	
	RegionProcesser mRProc;
	
	private final static float MIN_MOVE = 5f;

	private final static float CORNER_MAX = 50f;
	
	private int cornerMode = -1;
	
	public RegionProcesser getRegionProcessor() {
		return mRProc;
	}

	public void setRegionProcessor(RegionProcesser rProc) {
		mRProc = rProc;
		mVideoEditor.associateVideoRegionData(this);
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
	
	public void setCornerMode (float x, float y) {
		float[] points = {x,y};        	
    	iMatrix.mapPoints(points);
    	
    	float cSize = CORNER_MAX;
    	
    	cSize = iMatrix.mapRadius(cSize);
    	
    	if (Math.abs(mBounds.left-points[0])<cSize
    			&& Math.abs(mBounds.top-points[1])<cSize
    			)
    	{
    		cornerMode = 1;
    		return;
    	}
    	else if (Math.abs(mBounds.left-points[0])<cSize
    			&& Math.abs(mBounds.bottom-points[1])<cSize
    			)
    	{
    		cornerMode = 2;
			return;
		}
    	else if (Math.abs(mBounds.right-points[0])<cSize
    			&& Math.abs(mBounds.top-points[1])<cSize
    			)
    	{
    			cornerMode = 3;
    			return;
		}
    	else if (Math.abs(mBounds.right-points[0])<cSize
        			&& Math.abs(mBounds.bottom-points[1])<cSize
        			)
    	{
    		cornerMode = 4;
    		return;
    	}
    	
    	cornerMode = -1;
	}

	
	/* For touch events, whether or not to show the menu
	 */
	boolean moved = false;
				
	Matrix mMatrix, iMatrix;

	int fingerCount = 0;
	
	public VideoRegion(
			VideoEditor videoEditor,
			long duration,
			long l, long x,
			float left, float top, 
			float right, float bottom, Matrix matrix) 
	{
		//super(imageEditor);
		super();
		
		// Set the mImageEditor that this region belongs to to the one passed in
		mVideoEditor = videoEditor;
		// set the borders for tags in Non-Edit mode
		identifiedBorder = videoEditor.getResources().getDrawable(R.drawable.border_idtag);
		unidentifiedBorder = videoEditor.getResources().getDrawable(R.drawable.border);

		mMatrix = matrix;
		iMatrix = new Matrix();
    	mMatrix.invert(iMatrix);
		
		// Calculate the minMoveDistance using the screen density
		//float scale = this.getResources().getDisplayMetrics().density;
	//	minMoveDistance = minMoveDistanceDP * scale + 0.5f;
		
		
		mBounds = new RectF(left, top, right, bottom);	
		
        
        //set default processor
        this.setRegionProcessor(new PixelizeObscure());
    }
	
	public VideoRegion(
			VideoEditor videoEditor,
			long duration,
			long startTime, long endTime,
			PointF startPoint, int videoWidth, Matrix matrix) {
		
		float defaultSize = videoWidth/4;
		float halfSize = defaultSize/2;
		
		mBounds = new RectF();
		
		mBounds.left = startPoint.x - halfSize;
		mBounds.top = startPoint.y - halfSize;

		mBounds.right = startPoint.x + halfSize;
		mBounds.bottom = startPoint.y + halfSize;
				
		// Set the mImageEditor that this region belongs to to the one passed in
		mVideoEditor = videoEditor;
		// set the borders for tags in Non-Edit mode
		identifiedBorder = videoEditor.getResources().getDrawable(R.drawable.border_idtag);
		unidentifiedBorder = videoEditor.getResources().getDrawable(R.drawable.border);

		mMatrix = matrix;
		iMatrix = new Matrix();
    	mMatrix.invert(iMatrix);
		
		// Calculate the minMoveDistance using the screen density
		//float scale = this.getResources().getDisplayMetrics().density;
	//	minMoveDistance = minMoveDistanceDP * scale + 0.5f;
		
        
        //set default processor
        this.setRegionProcessor(new PixelizeObscure());
	}
	
	public void setMatrix (Matrix matrix)
	{
		mMatrix = matrix;
		iMatrix = new Matrix();
    	mMatrix.invert(iMatrix);
	}
	
	
	boolean isSelected ()
	{
		return selected;
	}
	
	void setSelected (boolean _selected)
	{
		selected = _selected;
	}
	
	public void setActive(boolean _active) {
		if(_active) {
			
		} else {
			
		}
	}
			
			
	private void updateBounds(float left, float top, float right, float bottom) 
	{
		Log.i(LOGTAG, "updateBounds: " + left + "," + top + "," + right + "," + bottom);
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
				
				Log.d(LOGTAG, "touching down");
				mVideoEditor.updateRegionDisplay();
				
				if (fingerCount == 1)
				{
					
					mStartPoint = new PointF(event.getX(),event.getY());
					Log.v(LOGTAG,"startPoint: " + mStartPoint.x + " " + mStartPoint.y);
				}
				
				moved = false;
				
				return false;
			case MotionEvent.ACTION_POINTER_UP:

				Log.v(LOGTAG, "second finger removed - pointer up!");

				return moved;
				
			case MotionEvent.ACTION_UP:

				mVideoEditor.updateRegionDisplay();
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
	                	
	                	if (cornerMode == -1)
	                		updateBounds(mBounds.left-diffX,mBounds.top-diffY,mBounds.right-diffX,mBounds.bottom-diffY);
	                	else if (cornerMode == 1)
	                		updateBounds(mBounds.left-diffX,mBounds.top-diffY,mBounds.right,mBounds.bottom);
	                	else if (cornerMode == 2)
	                		updateBounds(mBounds.left-diffX,mBounds.top,mBounds.right,mBounds.bottom-diffY);
	                	else if (cornerMode == 3)
	                		updateBounds(mBounds.left,mBounds.top-diffY,mBounds.right-diffX,mBounds.bottom);
	                	else if (cornerMode == 4)
	                		updateBounds(mBounds.left,mBounds.top,mBounds.right-diffX,mBounds.bottom-diffY);
	                		
	                	mStartPoint = new PointF(event.getX(),event.getY());
                	}
                	else
                	{
                		moved = false;
                	}
	            	
                }

                mVideoEditor.updateRegionDisplay();
					
				return true;
		}
		
		return false;
		
	}
	
	public void updateRegionProcessor (int obscureType) {
		
		switch (obscureType) {
			case VideoRegion.REDACT:
				Log.v(ObscuraConstants.TAG,"obscureType: SOLID");
				setRegionProcessor(new SolidObscure());
				break;
			case VideoRegion.PIXELATE:
				setRegionProcessor(new PixelizeObscure());
				break;
			case VideoRegion.CONSENT:
				// If the region processor is already a consent tagger, the user wants to edit.
				// so no need to change the region processor.
				if(!(getRegionProcessor() instanceof InformaTagger)) {
					setRegionProcessor(new InformaTagger());
					mVideoEditor.updateRegionDisplay();
				}
			
				mVideoEditor.launchTagger(this);
				break;
			default:
				setRegionProcessor(new PixelizeObscure());
				break;
		}
		
		if(getRegionProcessor().getClass() == InformaTagger.class)
			imageRegionBorder = identifiedBorder;
		else
			imageRegionBorder = unidentifiedBorder;
		
		mVideoEditor.updateRegionDisplay();
	}

	public boolean existsInTime(int time) {
		if (time < endTime && time >= startTime) {
			return true;
		}
		return false;
	}
	
	public String getStringData(float sizeMult) {
		//left, right, top, bottom, redact mode
		//return "" + (float)startTime/(float)1000 + ',' + (float)endTime/(float)1000 + ',' + (int)(sx*sizeMult) + ',' + (int)(ex*sizeMult) + ',' + (int)(sy*sizeMult) + ',' + (int)(ey*sizeMult) + ',' + currentMode;
		return "left, right, top, bottom, redact mode";
	}
}
