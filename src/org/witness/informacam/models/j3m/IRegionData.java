package org.witness.informacam.models.j3m;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IRegionBounds;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;

public class IRegionData extends Model {
	public List<IForm> associatedForms = new ArrayList<IForm>();
	public IRegionBounds regionBounds = null;
	public ILocation location = null;
	public long timestamp = 0L;
	public String id = null;
	public String index = null;
	
	public IRegionData() {
		super();
	}
	
	@Override
	public void inflate(JSONObject values) {
		try {
			if(values.has(Models.IRegion.INDEX)) {
				values = values.put(Models.IRegion.INDEX, Integer.toString(values.getInt(Models.IRegion.INDEX)));
			}
		} catch (JSONException e) {
			Logger.e(LOG, e);
		}
		
		super.inflate(values);
	}
	
	@Override
	public JSONObject asJson() {
		JSONObject obj = super.asJson();
		Logger.d(LOG, obj.toString());
		
		try {
			obj = obj.put(Models.IRegion.INDEX, Integer.parseInt(index));
		} catch (NumberFormatException e) {}
		catch (JSONException e) {
			Logger.e(LOG, e);
		}
		
		return obj;
	}
	
	@SuppressWarnings("unchecked")
	public IRegionData(IRegion region, ILocation location) {
		super();
		
		timestamp = region.timestamp;
		id = region.id;
		associatedForms = new ArrayList<IForm>();
		
		this.location = location;
		
		if(region.isInnerLevelRegion()) {
			this.regionBounds = region.bounds;
			
			// The reason why it's cast to a string is because if public field is null, it will be omitted.
			// you can't set an int to null, though, so...
			if(region.index > -1) {
				this.index = Integer.toString(region.index);
			}
		}		
		
		for(IForm form : region.associatedForms) {
			try {
				info.guardianproject.iocipher.FileInputStream is = new info.guardianproject.iocipher.FileInputStream(form.answerPath);
				JSONObject answerData = IOUtility.xmlToJson(is);
				
				Iterator<String> keys = answerData.keys();
				int keysFound = 0;
				while(keys.hasNext()) {
					keys.next();
					keysFound++;
				}
				
				if(keysFound > 0) {
					form.answerData = answerData;
					form.answerPath = null;
					form.title = null;
					
					associatedForms.add(form);
				}
			} catch (FileNotFoundException e) {
				Logger.e(LOG, e);
			}
		}
	}
}
