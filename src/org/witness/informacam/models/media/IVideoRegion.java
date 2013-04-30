package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

public class IVideoRegion extends IRegion {
	public List<IVideoTrail> trail = null;

	public IVideoRegion() {
		super();
	}
	
	public IVideoRegion(IRegion region) {
		super();
		inflate(region);
	}

	@Override
	public void init(IRegionBounds bounds) {
		trail = new ArrayList<IVideoTrail>();
		IVideoTrail v = new IVideoTrail(bounds.startTime, bounds);		
		trail.add(v);
		
		super.init(bounds);
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
