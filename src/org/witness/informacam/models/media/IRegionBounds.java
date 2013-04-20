package org.witness.informacam.models.media;

import org.witness.informacam.models.Model;

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
	
	public IRegionBounds() {}
	
	public IRegionBounds(int top, int left, int width, int height) {
		this(top, left, width, height, -1L, -1L);
	}
	
	public IRegionBounds(int top, int left, int width, int height, long startTime, long endTime) {
		this.displayTop = top;
		this.displayLeft = left;
		this.displayWidth = width;
		this.displayHeight = height;
		this.startTime = startTime;
		this.endTime = endTime;
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
}
