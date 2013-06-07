package org.witness.informacam.ui;

import org.witness.informacam.R;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IRegionBounds;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout.LayoutParams;

public class IRegionDisplay extends ImageView implements OnClickListener {
	
	Drawable d;
	Drawable activeD, inactiveD;
	LayoutParams lp;
	
	public IRegionBounds bounds;
	boolean isActive, isDragging;
	public IRegion parent;
	
	private final static String LOG = App.LOG;
	
	private IRegionDisplayListener mListener;
	
	public IRegionDisplay(Context context, IRegion parent, IRegionDisplayListener listener) {
		super(context);
		
		this.parent = parent;
		bounds = parent.bounds;
		mListener = listener;
		
		lp = new LayoutParams(bounds.displayWidth, bounds.displayHeight);
		lp.leftMargin = bounds.displayLeft;
		lp.topMargin = bounds.displayTop;
		setLayoutParams(lp);
		
		activeD = context.getResources().getDrawable(R.drawable.extras_region_display_active);
		inactiveD = context.getResources().getDrawable(R.drawable.extras_region_display_inactive);
		
		setStatus(true);
		setOnClickListener(this);
	}
	
	public void update() {
		Log.d(LOG, "new bounds left: " + bounds.displayLeft + " and top: " + bounds.displayTop);
		lp = (LayoutParams) getLayoutParams();
		lp.leftMargin = bounds.displayLeft;
		lp.topMargin = bounds.displayTop;
		setLayoutParams(lp);
		setImageDrawable(d);
	}
	
	public void setStatus(boolean isActive) {
		this.isActive = isActive;
		
		if(isActive) {
			d = activeD;
		} else {
			d = inactiveD;
		}
		
		update();
	}

	@Override
	public void onClick(View v) {
		setStatus(true);
	
		if (mListener != null)
			mListener.onSelected(this);
	}
}
