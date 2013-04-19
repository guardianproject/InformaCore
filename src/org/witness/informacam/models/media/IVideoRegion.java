package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

public class IVideoRegion extends IRegion {
	public List<IRegionBounds> trail = null;

	public IVideoRegion() {
		super();
	}

	@Override
	public void init(IRegionBounds bounds) {
		super.init(bounds);

		trail = new ArrayList<IRegionBounds>();
		trail.add(bounds);
	}

	public IRegionBounds getBoundsAtTime(long timestamp) {
		if(trail != null) {
			for(IRegionBounds bounds : trail) {
				if(timestamp == -1) {
					if(bounds.startTime == -1) {
						return bounds;
					}
				} else {
					if(bounds.startTime <= timestamp && timestamp <= bounds.endTime) {
						return bounds;
					}
				}
			}
		}
		return null;
	}
}
