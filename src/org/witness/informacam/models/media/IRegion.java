package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.ui.IRegionDisplay;

public class IRegion extends Model {
	public String id = null;
	public long timestamp = 0L;
	
	public String formNamespace = null;
	public String formPath = null;
	
	public IRegionBounds bounds;
	private IRegionDisplay regionDisplay;
	
	public void init(IRegionBounds bounds) {
		this.bounds = bounds;
		regionDisplay = new IRegionDisplay(InformaCam.getInstance().a, bounds);
		InformaCam.getInstance().informaService.addRegion(this);
	}
	
	public IRegionDisplay getRegionDisplay() {
		return regionDisplay;
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
