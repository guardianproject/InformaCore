package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;

public class IRegion extends Model {
	public String id = null;
	public long timestamp = 0L;
	
	public String formNamespace = null;
	public String formPath = null;
	
	public void init(IRegionBounds bounds) {
		InformaCam.getInstance().informaService.addRegion(this);
	}
	
	public void update() {
		InformaCam.getInstance().informaService.updateRegion(this);
	}
	
	public void delete(IMedia parent) {
		if(InformaCam.getInstance().informaService.removeRegion(this)) {
			parent.associatedRegions.remove(this);
		}
	}
}
