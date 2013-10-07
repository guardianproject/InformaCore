package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

import android.app.Activity;
import android.util.Log;

public class IVideoRegion extends IRegion {
	public List<IVideoTrail> trail = null;
	
	private long timestampInQuestion = 0;

	private IRegionDisplayListener mListener;
	
	public IVideoRegion() {
		super();
	}
	
	public IVideoRegion(IRegion region) {
		super();
		inflate(region);
	}

	@Override
	public void init(Activity context, IRegionBounds bounds, IRegionDisplayListener listener) {
		trail = new ArrayList<IVideoTrail>();
		IVideoTrail v = new IVideoTrail(bounds.startTime, bounds);		
		trail.add(v);
		mListener = listener;
		super.init(context, bounds, listener);
	}
	
	@Override
	public void update(Activity a) {
		InformaCam informaCam = InformaCam.getInstance();
		getBoundsAtTimestampInQuestion().calculate(mListener.getSpecs(),a);
		
		if (informaCam.informaService != null)
		informaCam.informaService.updateRegion(this);
	}
	
	public void setBoundsAtTime(long timestamp, IRegionBounds bounds) {
		IVideoTrail v = new IVideoTrail(timestamp, bounds);
		if(trail == null) {
			trail = new ArrayList<IVideoTrail>();
		}
		
		trail.add(v);
	}
	
	public IRegionBounds getBoundsAtTimestampInQuestion() {
		return getBoundsAtTime(timestampInQuestion);
	}
	
	public void setTimestampInQuestion(long timestamp) {
		timestampInQuestion = timestamp;
	}
	
	public IRegionBounds getBoundsAtTime(long timestamp) {
		// TODO: is this really going to work?
		Log.d(LOG, "TIMESTAMP : " + timestamp);
		
		if(trail != null) {
			int t = 0;
			for(IVideoTrail v : trail) {
				if(v.timestamp == timestamp) {
					return v.bounds;
				} else if(v.timestamp > timestamp) {
					return trail.get(t - 1).bounds;
				}
				
				t++;
			}
		}
		
		return trail.get(0).bounds;
	}
}
