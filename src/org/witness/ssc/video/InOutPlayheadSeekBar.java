/*
 * Thanks to: http://www.quicknews4you.com/2011/05/08/android-seek-bar-with-two-thumb/
 * for the sample code, much of which as been incorporated
 */

package org.witness.ssc.video;

import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.ssc.R;
import org.witness.ssc.utils.ObscuraConstants;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.SeekBar;

public class InOutPlayheadSeekBar extends SeekBar {

	private String LOGTAG = ObscuraConstants.TAG;
	
	public static final int NONE = 0;
	public static final int THUMBIN = 1;
	public static final int THUMBOUT = 2;
	
	private int selectedThumb = NONE;
	
	private Bitmap thumbIn = BitmapFactory.decodeResource(getResources(), R.drawable.leftthumb);
	private Bitmap thumbOut = BitmapFactory.decodeResource(getResources(), R.drawable.rightthumb);
	
	private int thumbInX, thumbOutX;
	private int thumbInY, thumbOutY;
	private int thumbInValue, thumbOutValue;
	
	private int thumbInHalfWidth;
	private int thumbOutHalfWidth;
	
	private Paint paint = new Paint();
	
	private InOutPlayheadSeekBarChangeListener changeListener;
	
	public boolean thumbsActive = false;
	
	public void setThumbsActive(int inThumbValue, int outThumbValue) {
		Log.v(LOGTAG,"in value: " + inThumbValue + " out value: " + outThumbValue);
		thumbsActive = true;
		setThumbsValue(inThumbValue, outThumbValue);
		invalidate();
	}
	
	public void setThumbsActive(VideoRegion region) {
		//int inThumbValue = (int)(region.startTime/region.mediaDuration*100);
		//int outThumbValue = (int)(region.endTime/region.mediaDuration*100);
		int inThumbValue, outThumbValue;
		try {
			inThumbValue = mapSecondsToPixels((Long) region.mProps.get(Keys.VideoRegion.START_TIME), (Long) region.mProps.get(Keys.VideoRegion.DURATION));
		} catch(ClassCastException e) {
			inThumbValue = mapSecondsToPixels((long) ((Integer) region.mProps.get(Keys.VideoRegion.START_TIME)), (Long) region.mProps.get(Keys.VideoRegion.DURATION));
		}
		try {
			outThumbValue = mapSecondsToPixels((Long) region.mProps.get(Keys.VideoRegion.END_TIME), (Long) region.mProps.get(Keys.VideoRegion.DURATION));
		} catch(ClassCastException e) {
			outThumbValue = mapSecondsToPixels((long) ((Integer) region.mProps.get(Keys.VideoRegion.END_TIME)), (Long) region.mProps.get(Keys.VideoRegion.DURATION));
		}
		
		Log.v(LOGTAG, "REALLY, setting new thumb values:\nin= " + inThumbValue + ". out= " + outThumbValue);
		Log.v(LOGTAG, "although the region says:\nin= " + region.startTime + ". out= " + region.endTime);
		
		thumbsActive = true;
		setThumbsValue(inThumbValue, outThumbValue);
		invalidate();
	}
	
	public void setThumbsInactive() {
		thumbsActive = false;
	}
	
	public InOutPlayheadSeekBar(Context context) {
		super(context);
	}
	
	public InOutPlayheadSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public InOutPlayheadSeekBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	public int mapPixelsToSeconds(int pixels, long duration) {
		return (int) (pixels * duration/getWidth());
	}
	
	public int mapSecondsToPixels(long seconds, long duration) {
		return (int) (seconds * getWidth()/duration);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (getHeight() > 0) {
			init();
		}
	}
	
	private void init() 
	{		
		thumbInY = (getHeight() / 2) - (thumbIn.getHeight() / 2);
		thumbOutY = (getHeight() / 2) - (thumbOut.getHeight() / 2);
		
		thumbInHalfWidth = thumbIn.getWidth()/2;
		thumbInX = 0;
		
		thumbOutHalfWidth = thumbOut.getWidth()/2;
		thumbOutX = getWidth()-thumbOutHalfWidth;
		
		invalidate();
	}
	
	public void setInOutPlayheadSeekBarChangeListener(InOutPlayheadSeekBarChangeListener changeListener){
		this.changeListener = changeListener;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (thumbsActive) {
			canvas.drawBitmap(thumbIn, thumbInX - thumbInHalfWidth, thumbInY, paint);
			canvas.drawBitmap(thumbOut, thumbOutX - thumbOutHalfWidth, thumbOutY, paint);
		} else 
			Log.d(LOGTAG, "inactive bars!");
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean handled = false;
		
		int mx = (int) event.getX();
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (mx >= thumbInX - thumbInHalfWidth && mx <= thumbInX + thumbInHalfWidth) {
				selectedThumb = THUMBIN;
				Log.i(LOGTAG,"Select thumbIn");
				handled = true;
			} else if (mx >= thumbOutX - thumbOutHalfWidth && mx <= thumbOutX + thumbOutHalfWidth) {
				selectedThumb = THUMBOUT;
				Log.i(LOGTAG,"Select thumbOut");
				handled = true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			Log.i(LOGTAG,"Mouse Move : " + selectedThumb);

			if (selectedThumb == THUMBIN) {
				thumbInX = mx;
				Log.i(LOGTAG,"Move thumbIn");
				handled = true;
			} else if (selectedThumb == THUMBOUT) {
				thumbOutX = mx;
				Log.i(LOGTAG,"Move thumbOut");
				handled = true;
			}
			break;
		case MotionEvent.ACTION_UP:
			selectedThumb = NONE;
			Log.d(LOGTAG, "1. hey new thumb vals:\nthumbIn: " + thumbInX + " thumbOut: " + thumbOutX);
			handled = true;
			break;
		}
		
		// Some constraints
		if (thumbInX < 0) {
			thumbInX = 0;
		}
		
		if (thumbOutX < thumbInX) {
			thumbOutX = thumbInX;
		}
			
		if (thumbOutX > getWidth()) {
			thumbOutX = getWidth();
		}
		
		if (thumbInX > thumbOutX) {
			thumbInX = thumbOutX;
		}
		
		invalidate();
		
		if (changeListener != null) {
			Log.d(LOGTAG, "2. hey new thumb vals:\nthumbIn: " + thumbInX + " thumbOut: " + thumbOutX);
			calculateThumbsValue();
			changeListener.inOutValuesChanged(thumbInValue,thumbOutValue);
		}
		
		if (!handled) {
			super.onTouchEvent(event);
		}
		
		return true;
	}
	
	private void calculateThumbsValue(){
		thumbInValue = (int)((100*((float)thumbInX))/((float)getWidth()));
		thumbOutValue = (int)((100*((float)thumbOutX))/((float)getWidth()));
		Log.v(LOGTAG,"thumb in value: " + thumbInValue + " thumb out value: " + thumbOutValue);
	}
	
	private void setThumbsValue(int thumbInValue, int thumbOutValue) {
		//thumbInX = (int)(((float)thumbInValue/(float)100)*(float)getWidth());
		//thumbOutX = (int)(((float)thumbOutValue/(float)100)*(float)getWidth());
		thumbInX = thumbInValue;
		thumbOutX = thumbOutValue;
		Log.v(LOGTAG,"thumbInX: " + thumbInX + " thumbOutX: " + thumbOutX);
		calculateThumbsValue();
	}
		
	interface InOutPlayheadSeekBarChangeListener {
		void inOutValuesChanged(int thumbInValue,int thumbOutValue);
	}	
}
