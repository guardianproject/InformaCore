package org.witness.informacam.models.j3m;

import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IRegionBounds;

public class IRegionData extends Model {
	public String namespace;
	public JSONObject metadata;
	public IRegionBounds regionBounds;
	public ILocation location;
	public long timestamp;
	
	public IRegionData() {
		super();
	}
	
	public IRegionData(IRegion region, JSONObject metadata) {
		super();
		
		this.namespace = region.formNamespace;
		this.metadata = metadata;
		this.regionBounds = region.bounds;
		this.timestamp = region.timestamp;
	}
}
