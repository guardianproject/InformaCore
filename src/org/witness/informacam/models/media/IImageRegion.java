package org.witness.informacam.models.media;

public class IImageRegion extends IRegion {
	public IRegionBounds bounds = null;

	public IImageRegion() {
		super();
	}
	
	public IImageRegion(IRegion region) {
		super();
		inflate(region);
	}
	
}
