package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

public class IVideoRegion extends IRegion {
	public List<IVideoTrail> trail = null;

	public IVideoRegion() {
		super();
	}

	@Override
	public void init(IRegionBounds bounds) {
		super.init(bounds);

		trail = new ArrayList<IVideoTrail>();
		IVideoTrail v = new IVideoTrail(bounds.startTime, bounds);		
		trail.add(v);		
	}
	
	public void setBoundsAtTime(long timestamp, IRegionBounds bounds) {
		IVideoTrail v = new IVideoTrail(timestamp, bounds);
		if(trail == null) {
			trail = new ArrayList<IVideoTrail>();
		}
		
		trail.add(v);
	}
	
	public IRegionBounds getBoundsAtTime(long timestamp) {
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
		return null;
	}
}
