package org.witness.informacam.ui;

import org.witness.informacam.R;
import org.witness.informacam.models.media.IRegionBounds;
import org.witness.informacam.utils.Constants.App;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout.LayoutParams;

public class IRegionDisplay extends ImageView implements OnClickListener {
	Activity a;
	
	Drawable d;
	Drawable activeD, inactiveD;
	LayoutParams lp;
	
	public IRegionBounds bounds;
	boolean isActive, isDragging;
	
	public interface IRegionDisplayListener {
		public void onSelected(IRegionDisplay regionDisplay);
	}
	
	private final static String LOG = App.LOG;
	
	public IRegionDisplay(Activity a, IRegionBounds bounds) {
		super(a);
		
		this.a = a;
		this.bounds = bounds;
		Log.d(LOG, bounds.asJson().toString());
		
		lp = new LayoutParams(bounds.displayWidth, bounds.displayHeight);
		lp.leftMargin = bounds.displayLeft;
		lp.topMargin = bounds.displayTop;
		setLayoutParams(lp);
		
		activeD = a.getResources().getDrawable(R.drawable.extras_region_display_active);
		inactiveD = a.getResources().getDrawable(R.drawable.extras_region_display_inactive);
		
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
		((IRegionDisplayListener) a).onSelected(this);
	}

	/*
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		Log.d(LOG, "regiondisplay has the ontouch listener:\naction:" + event.getAction() + "\nx: " + event.getX() + ", y: " + event.getY());
		
		if(event.getAction() == MotionEvent.ACTION_MOVE) {
			isDragging = true;
		}
		
		if(event.getAction() == MotionEvent.ACTION_CANCEL && isDragging) {
			isDragging = false;
			bounds.displayLeft = (int) event.getX() - (bounds.displayWidth/2);
			bounds.displayTop = (int) event.getY() - (bounds.displayHeight/2);
			update();
		}
		return false;
	}
	*/
}
