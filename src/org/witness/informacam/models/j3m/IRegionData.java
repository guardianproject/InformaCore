package org.witness.informacam.models.j3m;

import java.io.FileNotFoundException;

import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IRegionBounds;
import org.witness.informacam.storage.IOUtility;

import android.util.Log;

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

	public IRegionData(IForm form, String formPath) {
		try {
			info.guardianproject.iocipher.FileInputStream is = new info.guardianproject.iocipher.FileInputStream(formPath);
			
			this.namespace = form.namespace;
			this.metadata = IOUtility.xmlToJson(is);
			
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		
	}
}
