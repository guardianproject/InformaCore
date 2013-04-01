package org.witness.informacam.models;

import org.json.JSONObject;

public class IResult extends Model {
	public int code = 0;
	public String reason = null;
	public JSONObject data = null;
	public int responseCode = 0;
	
	public IResult() {}
	
	public IResult(int code, JSONObject data) {
		this.code = code;
		this.data = data;
	}
	
	public IResult(int code, String reason) {
		this.code = code;
		this.reason = reason;
	}

}