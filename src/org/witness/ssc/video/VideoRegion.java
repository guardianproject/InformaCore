package org.witness.ssc.video;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.VideoRegions;
import org.witness.ssc.image.filters.CrowdPixelizeObscure;
import org.witness.ssc.image.filters.InformaTagger;
import org.witness.ssc.image.filters.PixelizeObscure;
import org.witness.ssc.image.filters.RegionProcesser;
import org.witness.ssc.image.filters.SolidObscure;
import org.witness.ssc.utils.ObscuraConstants;

import android.graphics.RectF;
import android.util.Log;

public class VideoRegion {

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
	public static final String LOGTAG = ObscuraConstants.TAG;

	public static final String DEFAULT_MODE = "pixel";

	
	public static final long DEFAULT_LENGTH = 10; // Seconds
	
	public static final float DEFAULT_X_SIZE = 150;
	public static final float DEFAULT_Y_SIZE = 150;
		
	public float sx = 0;
	public float sy = 0;
	
	public float ex = 0;
	public float ey = 0;
		
	public long startTime = 0;
	public long endTime = 0;
	
	public long mediaDuration = 0;
	
	public String currentMode = DEFAULT_MODE;
	public static final int REDACT = 0; // PaintSquareObscure
	public static final int PIXELATE = 1; // PixelizeObscure
	public static final int CONSENT = 2; // PixelizeObscure
	
	public VideoEditor mVideoEditor;
	public Map<String, Object> mProps;
	
	RegionProcesser mRProc;
	
	public VideoRegion(VideoEditor videoEditor, long _duration, long _startTime, long _endTime, float _sx, float _sy, float _ex, float _ey, String _mode, VideoRegion parentRegion) {
		mVideoEditor = videoEditor;
		mediaDuration = _duration;
		startTime = _startTime;
		endTime = _endTime;
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

		mProps = new HashMap<String, Object>();
		mProps.put(Keys.VideoRegion.DURATION, mediaDuration);
		mProps.put(Keys.VideoRegion.START_TIME, startTime);
		mProps.put(Keys.VideoRegion.END_TIME, endTime);
		mProps.put(Keys.VideoRegion.CHILD_REGIONS, new Vector<VideoRegion>());
		
		try {
			mProps.put(Keys.VideoRegion.PARENT_REGION, parentRegion);
			((Vector<VideoRegion>) parentRegion.mProps.get(Keys.VideoRegion.CHILD_REGIONS)).add(this);
			parentRegion.mProps.put(Keys.VideoRegion.END_TIME, endTime);
		} catch(NullPointerException e) {
			mProps.put(Keys.VideoRegion.PARENT_REGION, null);
		}
		
		setRegionProcessor(new PixelizeObscure());
		Log.d(VideoEditor.LOGTAG, mProps.toString());
	}

	public VideoRegion(VideoEditor videoEditor, long _duration, long _startTime, float _sx, float _sy, float _ex, float _ey, VideoRegion parentRegion) {
		this(videoEditor, _duration, _startTime, _startTime+DEFAULT_LENGTH, _sx, _sy, _ex, _ey, DEFAULT_MODE, parentRegion);
	}

	public VideoRegion(VideoEditor videoEditor, long _duration, long _startTime, long _endTime, float _sx, float _sy, VideoRegion parentRegion) {
		this(videoEditor, _duration, _startTime, _endTime, _sx - DEFAULT_X_SIZE/2, _sy - DEFAULT_Y_SIZE/2, _sx + DEFAULT_X_SIZE/2, _sy + DEFAULT_Y_SIZE/2, DEFAULT_MODE, parentRegion);
	}

	public VideoRegion(VideoEditor videoEditor, long _duration, long _startTime, float _sx, float _sy, VideoRegion parentRegion) {
		this(videoEditor, _duration, _startTime, _startTime+DEFAULT_LENGTH, _sx - DEFAULT_X_SIZE/2, _sy - DEFAULT_Y_SIZE/2, _sx + DEFAULT_X_SIZE/2, _sy + DEFAULT_Y_SIZE/2, DEFAULT_MODE, parentRegion);
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
		try {
			startTime = (Long) mProps.get(Keys.VideoRegion.START_TIME);
		} catch(ClassCastException e) {
			startTime = (long) ((Integer) mProps.get(Keys.VideoRegion.START_TIME));
		}
		
		try {
			endTime = (Long) mProps.get(Keys.VideoRegion.END_TIME);
		} catch(ClassCastException e) {
			endTime = (long) ((Integer) mProps.get(Keys.VideoRegion.END_TIME));
		}
		
		if (time < endTime && time >= startTime) {
			return true;
		}
		return false;
	}

	public String getStringData(float sizeMult) {
		//left, right, top, bottom
		return "" + (float)startTime/(float)1000 + ',' + (float)endTime/(float)1000 + ',' + (int)(sx*sizeMult) + ',' + (int)(ex*sizeMult) + ',' + (int)(sy*sizeMult) + ',' + (int)(ey*sizeMult) + ',' + currentMode;
	}
	
	//TODO: maybe we are not using the same region processors...
	public void setRegionProcessor(RegionProcesser rProc) {
		mRProc = rProc;
		mVideoEditor.associateVideoRegionData(this);
	}
	
	public RegionProcesser getRegionProcessor() {
		return mRProc;
	}
	
	public void updateRegionProcessor (int obscureType) {
		
		switch (obscureType) {
			case REDACT:
				Log.v(ObscuraConstants.TAG,"obscureType: SOLID");
				setRegionProcessor(new SolidObscure());
				break;
			case PIXELATE:
				setRegionProcessor(new PixelizeObscure());
				break;
			case CONSENT:
				// If the region processor is already a consent tagger, the user wants to edit.
				// so no need to change the region processor.
				if(!(getRegionProcessor() instanceof InformaTagger)) {
					setRegionProcessor(new InformaTagger());
					//mVideoEditor.updateRegionDisplay();
				}
			
				mVideoEditor.launchTagger(this);
				break;
			default:
				setRegionProcessor(new PixelizeObscure());
				break;
		}
		
		//mVideoEditor.updateRegionDisplay();
	}
	
	public JSONObject getRepresentation() throws JSONException {
		JSONObject representation = new JSONObject();
		Iterator<Entry<String, Object>> i = mProps.entrySet().iterator();
		
		while(i.hasNext()) {
			Entry<String, Object> e = i.next();
			representation.put(e.getKey(), e.getValue());
		}
		
		return representation;
	}
}