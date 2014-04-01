package org.witness.informacam.models.credentials;

import java.io.Serializable;

import org.json.JSONObject;
import org.witness.informacam.models.Model;

@SuppressWarnings("serial")
public class IPreferences extends Model implements Serializable {
	public boolean encryptOriginals = false;
	
	public IPreferences() {
		super();
	}
	
	public IPreferences(IPreferences preferences) {
		super();
		inflate(preferences.asJson());
	}
	
	public IPreferences(JSONObject preferences) {
		super();
		inflate(preferences);
	}

}