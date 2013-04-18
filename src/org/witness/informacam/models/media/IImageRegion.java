package org.witness.informacam.models.media;

public class IImageRegion extends IRegion {
	public IRegionBounds bounds = null;

	public IImageRegion() {
		super();
	}
	
	@Override
	public void init(IRegionBounds bounds) {
		this.bounds = bounds;
		super.init(bounds);
	}
	
}
