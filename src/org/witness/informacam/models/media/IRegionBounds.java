package org.witness.informacam.models.media;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class IRegionBounds extends Model {
	public int top = 0;
	public int left = 0;
	public int width = 0;
	public int height = 0;
	public long startTime = -1L;
	public long endTime = 0L;
	
	public IRegionBounds(int top, int left, int width, int height) {
		this(top, left, width, height, -1L);
	}
	
	public IRegionBounds(int top, int left, int width, int height, long startTime) {
		this.top = top;
		this.left = left;
		this.width = width;
		this.height = height;
		this.startTime = startTime;
	}
	
	public long getDuration() {
		if(startTime != -1L) {
			return Math.abs(startTime - endTime);
		} else {
			return 0;
		}
	}
	
	@Override
	public JSONObject asJson() {
		JSONObject regionBounds = new JSONObject();
		
		JSONObject timestamps = null;
		JSONObject regionDimensions = null;
		JSONObject regionCoordinates = null;
		
		try {
			regionDimensions = new JSONObject();
			regionDimensions.put(Models.IRegion.Bounds.HEIGHT, height);
			regionDimensions.put(Models.IRegion.Bounds.WIDTH, width);
			regionBounds.put(Models.IRegion.REGION_COORDINATES, regionCoordinates);
		
			regionCoordinates = new JSONObject();
			regionCoordinates.put(Models.IRegion.Bounds.TOP, top);
			regionCoordinates.put(Models.IRegion.Bounds.LEFT, left);
			regionBounds.put(Models.IRegion.REGION_DIMENSIONS, regionDimensions);
			
			if(startTime != -1L) {
				timestamps = new JSONObject();
				timestamps.put(Models.IRegion.Bounds.START_TIME, startTime);
				timestamps.put(Models.IRegion.Bounds.END_TIME, endTime);
				timestamps.put(Models.IRegion.Bounds.DURATION, getDuration());
				regionBounds.put(Models.IRegion.REGION_TIMESTAMPS, timestamps);
			}
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		
		return regionBounds;
	}
	
}
