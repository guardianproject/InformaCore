package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

public class IVideoRegion extends IRegion {
	public List<IRegionBounds> trail = null;
	
	@Override
	public void init(IRegionBounds bounds) {
		super.init(bounds);
		
		trail = new ArrayList<IRegionBounds>();
		trail.add(bounds);
	}
}
