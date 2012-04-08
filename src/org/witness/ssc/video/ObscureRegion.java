package org.witness.ssc.video;

import java.util.HashMap;
import java.util.Map;

import org.witness.ssc.utils.ObscuraConstants;

import org.witness.ssc.R;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

public class ObscureRegion {

	/*
	 * Thinking about whether or not a region should contain multiple start/end times
	 * realizing that doing this would make editing a real pita
	 * Of course, it would make displaying be a 1000x better though.
	class PositionTime {

		int sx = 0; 
		int sy = 0; 
		int ex = 0;
		int ey = 0;
		long startTime = 0; 
		long endTime = 0;
		
		PositionTime(int _sx, int _sy, int _ex, int _ey, long _startTime, long _endTime) {
			
		}
	}
	*/
	
	public static final String LOG = ObscuraConstants.TAG;

	public static final String DEFAULT_MODE = ObscuraConstants.Filters.VIDEO_PIXELIZE;

	
	public static final long DEFAULT_LENGTH = 10; // Seconds
	
	public static final float DEFAULT_X_SIZE = 150;
	public static final float DEFAULT_Y_SIZE = 150;
		
	public float sx = 0;
	public float sy = 0;
	
	public float ex = 0;
	public float ey = 0;
		
	public int startTime = 0;
	public int endTime = 0;
	
	public int mediaDuration = 0;
	
	public String currentMode = DEFAULT_MODE;
	
	VideoEditor videoEditor;
	
	public Map<String, Object> properties;
	
	public Breakpoint breakpoint;
	int breakpointOffset;
	
	public ObscureRegion(VideoEditor _videoEditor, long _duration, long _startTime, long _endTime, float _sx, float _sy, float _ex, float _ey, String _mode) {
		videoEditor = _videoEditor;
		mediaDuration = (int) _duration;
		startTime = (int) _startTime;
		endTime = (int) _endTime;
		sx = _sx;
		sy = _sy;
		ex = _ex;
		ey = _ey;
		
		if (sx < 0) { 
			sx = 0;
		} else if (sy < 0) {
			sy = 0;
		}
		
		currentMode = _mode;
		properties = new HashMap<String, Object>();
		Log.d(LOG, "new region created: " + sx + ", " + sy + ", " + ex + ", " + ey);
		Log.d(LOG, "times: " + startTime + " to " + endTime);
		
		breakpoint = new Breakpoint(videoEditor);
		
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.DURATION, endTime - startTime);
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_TIME, mediaDuration);
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_WIDTH, videoEditor.breakpointWidth);
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.LEFT, breakpointOffset + breakpoint.mapSecondsToPixels(startTime));
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.RIGHT, breakpoint.mapSecondsToPixels(endTime));
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.IN, startTime);
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.OUT, endTime);
		properties.put(ObscuraConstants.VideoEditor.Breakpoints.FILTER, DEFAULT_MODE);
		
		Log.d(LOG, properties.toString());
		breakpoint.set();
	}

	public ObscureRegion(VideoEditor _videoEditor, long _duration, long _startTime, float _sx, float _sy, float _ex, float _ey) {
		this(_videoEditor, _duration, _startTime, _startTime+DEFAULT_LENGTH, _sx, _sy, _ex, _ey, DEFAULT_MODE);
	}

	public ObscureRegion(VideoEditor _videoEditor, long _duration, long _startTime, long _endTime, float _sx, float _sy) {
		this(_videoEditor, _duration, _startTime, _endTime, _sx - DEFAULT_X_SIZE/2, _sy - DEFAULT_Y_SIZE/2, _sx + DEFAULT_X_SIZE/2, _sy + DEFAULT_Y_SIZE/2, DEFAULT_MODE);
	}

	public ObscureRegion(VideoEditor _videoEditor, long _duration, long _startTime, float _sx, float _sy) {
		this(_videoEditor, _duration, _startTime, _startTime+DEFAULT_LENGTH, _sx - DEFAULT_X_SIZE/2, _sy - DEFAULT_Y_SIZE/2, _sx + DEFAULT_X_SIZE/2, _sy + DEFAULT_Y_SIZE/2, DEFAULT_MODE);
	}
	
	public void moveRegion(float _sx, float _sy) {
		moveRegion(_sx - DEFAULT_X_SIZE/2, _sy - DEFAULT_Y_SIZE/2, _sx + DEFAULT_X_SIZE/2, _sy + DEFAULT_Y_SIZE/2);
	}
	
	public void moveRegion(float _sx, float _sy, float _ex, float _ey) {
		sx = _sx;
		sy = _sy;
		ex = _ex;
		ey = _ey;
	}
	
	public RectF getRectF() {
		return new RectF(sx, sy, ex, ey);
	}
	
	public RectF getBounds() {
		return getRectF();
	}
	
	
	public boolean existsInTime(long time) {
		if (time < endTime && time >= startTime) {
			return true;
		}
		return false;
	}

	public String getStringData(float sizeMult) {
		//left, right, top, bottom
		return "" + (float)startTime/(float)1000 + ',' + (float)endTime/(float)1000 + ',' + (int)(sx*sizeMult) + ',' + (int)(ex*sizeMult) + ',' + (int)(sy*sizeMult) + ',' + (int)(ey*sizeMult) + ',' + currentMode;
	}
	
	public void setProperty(String tag, Object value) {
		if(properties == null)
			properties = new HashMap<String, Object>();
		
		properties.put(tag, value);
		
		try {
			properties.put(ObscuraConstants.VideoEditor.Breakpoints.LEFT, breakpointOffset + breakpoint.mapSecondsToPixels(startTime));
			properties.put(ObscuraConstants.VideoEditor.Breakpoints.RIGHT, breakpoint.mapSecondsToPixels(endTime));
		} catch(NullPointerException e) {}
	}
	
	public class Breakpoint extends ImageButton {
		RelativeLayout.LayoutParams lp;
		
		public Breakpoint(Context context) {
			super(context);
			setStyle();
			setOnClickListener(videoEditor);
		}
		
		public Breakpoint(Context context, AttributeSet attrs) {
			super(context, attrs);
			setStyle();
			setOnClickListener(videoEditor);
		}
		
		public void setStyle() {
			this.setBackgroundDrawable(videoEditor.getResources().getDrawable(R.drawable.breakpoint_background));
		}
		
		public int mapPixelsToSeconds(int pixels) {
			Log.d(LOG, "total time: " + (Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_TIME));
			return (int) (pixels * (Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_TIME))/(Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_WIDTH);
		}
		
		public int mapSecondsToPixels(int seconds) {
			return (int) (seconds * (Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_WIDTH)/(Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.TOTAL_TIME));
		}
		
		public void redraw() {
			int dif = (Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.OUT) - (Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.IN);
			lp = new RelativeLayout.LayoutParams(mapSecondsToPixels(dif), videoEditor.breakpointHeight - 25);
			lp.setMargins((Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.LEFT), videoEditor.breakpointTop - 10, 0, 0);
			this.setLayoutParams(lp);
		}
		
		public void set() {
			
			lp = new RelativeLayout.LayoutParams(mapSecondsToPixels((Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.DURATION)), videoEditor.breakpointHeight - 25);
			lp.setMargins((Integer) properties.get(ObscuraConstants.VideoEditor.Breakpoints.LEFT), videoEditor.breakpointTop - 10, 0, 0);
			this.setLayoutParams(lp);
			
			videoEditor.breakpointHolder.addView(this);
		}
		
		public ObscureRegion getRegion() {
			return ObscureRegion.this;
		}
	}
}