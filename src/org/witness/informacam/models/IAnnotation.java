package org.witness.informacam.models;

import org.json.JSONObject;

public class IAnnotation extends Model {
	public long timestamp;
	public String namespace;
	public ILocation location;
	public JSONObject content;
}
