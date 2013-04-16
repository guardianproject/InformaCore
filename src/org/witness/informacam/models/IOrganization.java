package org.witness.informacam.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;

public class IOrganization extends Model {
	public String organizationName = null;
	public String organizationDetails = null;
	public String requestUrl = null;
	public String publicKeyPath = null;
	public String organizationFingerprint = null;
	public ITransportCredentials transportCredentials = new ITransportCredentials();
	public IIdentity identity = new IIdentity();
	
	public JSONObject inflateContent(String contentString) {
		try {
			return (JSONObject) new JSONTokener(contentString).nextValue();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}
}
