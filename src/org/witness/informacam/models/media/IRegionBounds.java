package org.witness.informacam.models.media;

import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Models;

public class IRegionBounds extends Model {
	public int top = 0;
	public int left = 0;
	public int width = 0;
	public int height = 0;
	public long startTime = -1L;
	public long endTime = 0L;
	
	public int displayTop = 0;
	public int displayLeft = 0;
	public int displayWidth = 0;
	public int displayHeight = 0;
	
	public IRegionBounds(int top, int left, int width, int height) {
		this(top, left, width, height, -1L);
	}
	
	public IRegionBounds(int top, int left, int width, int height, long startTime) {
		this.displayTop = top;
		this.displayLeft = left;
		this.displayWidth = width;
		this.displayHeight = height;
		this.startTime = startTime;
	}
	
	public long getDuration() {
		if(startTime != -1L) {
			return Math.abs(startTime - endTime);
		} else {
			return 0;
		}
	}
	
	private void calculate() {
		// TODO: turn display metrics into real metrics;
	}
	
	@Override
	public JSONObject asJson() {
		JSONObject json = super.asJson();
		/*
		json.remove(Models.IRegion.DISPLAY_TOP);
		json.remove(Models.IRegion.DISPLAY_LEFT);
		json.remove(Models.IRegion.DISPLAY_WIDTH);
		json.remove(Models.IRegion.DISPLAY_HEIGHT);
		*/
		
		if(startTime == -1L) {
			json.remove(Models.IRegion.Bounds.START_TIME);
			json.remove(Models.IRegion.Bounds.END_TIME);
			json.remove(Models.IRegion.Bounds.DURATION);
		}
		
		return json;
	}
	
}
