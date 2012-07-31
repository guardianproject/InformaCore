package org.witness.informacam.informa;

import org.json.JSONException;
import org.json.JSONObject;

public class LogPack extends JSONObject {
	public LogPack() {}
	
	public LogPack(String key, Object value) {
		try {
			this.put(key, value);
		} catch(JSONException e) {}
	}
}
