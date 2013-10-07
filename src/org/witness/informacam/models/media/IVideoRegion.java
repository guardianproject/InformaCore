package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;
import org.witness.informacam.utils.Constants.Logger;

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
		Log.d(LOG, "start time: " + bounds.startTime);
		
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
		// TODO: TreeSet logic...
		Log.d(LOG, "TIMESTAMP : " + timestamp);
		Log.d(LOG, "startTime: " + bounds.startTime);
		Log.d(LOG, "endTime: " + bounds.endTime);
		
		if(trail != null) {
			int t = 0;
			for(IVideoTrail v : trail) {
				Log.d(LOG, "TRAIL:\n" + v.asJson().toString());
				
				if(v.timestamp == timestamp) {
					return v.bounds;
				}
				
				t++;
			}
		}
		
		return null;
	}
}
