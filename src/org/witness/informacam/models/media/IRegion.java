package org.witness.informacam.models.media;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;

import android.util.Log;

public class IRegion extends Model {
	public String id = null;
	
	public void init(IRegionBounds bounds) {
		try {
			InformaCam.getInstance().informaService.addRegion(this);
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public void update() {
		
	}
	
	public void delete() {
		
	}
}
