package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

public class IVideoRegion extends IRegion {
	public List<IVideoTrail> trail = null;
	public int timestampInQuestion = 0;

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
	
	@Override
	public void update() {
		InformaCam informaCam = InformaCam.getInstance();
		getBoundsAtTimestampInQuestion().calculate(((IRegionDisplayListener) informaCam.a).getSpecs());
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
	
	public IRegionBounds getBoundsAtTime(long timestamp) {
		// TODO: is this really going to work?
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
