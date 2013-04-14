package org.witness.informacam.ui;

import org.witness.informacam.models.media.IRegionBounds;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class IRegionDisplay extends View {
	Drawable d;
	Drawable activeD, inactiveD;
	IRegionBounds bounds;
	LayoutParams lp;
	
	public IRegionDisplay(Context context, IRegionBounds bounds) {
		super(context);
		
		this.bounds = bounds;
		
		lp = new LayoutParams(bounds.displayWidth, bounds.displayHeight);
		setLayoutParams(lp);
		
		
	}
	
	public void setStatus(boolean isActive) {
		if(isActive) {
			d = activeD;
		} else {
			d = inactiveD;
		}
	}
}
