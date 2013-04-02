package org.witness.informacam.models.media;

import org.witness.informacam.models.Model;

import android.graphics.RectF;

public class IRect extends Model {
	RectF displayRect = null;
	RectF realRect = null;
	
	public int width = 0;
	public int height = 0;
	public int[] displayCoordinates = null;
	public int[] realCoordinates = null;
		
	public int[] getDisplayCoordinates() {
		return new int[] {(int) displayRect.top, (int) displayRect.left};
	}
	
	public int[] getRealCoordinates() {
		return new int[] {(int) realRect.top, (int) realRect.left};
	}
	
	public void update() {
		// TODO:
	}
}
