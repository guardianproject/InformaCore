package org.witness.informacam.models.organizations;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.credentials.IIdentity;
import org.witness.informacam.models.forms.IForm;

import android.util.Log;

public class IOrganization extends Model {
	public String organizationName = null;
	public String organizationDetails = null;
	public String publicKeyPath = null;
	public String organizationFingerprint = null;
	public List<IRepository> repositories = new ArrayList<IRepository>();
	public List<IForm> forms = new ArrayList<IForm>();
	
	public IOrganization() {
		super();
	}
	
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
