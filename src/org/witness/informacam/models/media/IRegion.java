package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.ui.IRegionDisplay;

public class IRegion extends Model {
	public String id = null;
	public long timestamp = 0L;

	public String formNamespace = null;
	public String formPath = null;

	public IRegionBounds bounds = null;
	private IRegionDisplay regionDisplay = null;
	
	public IRegion() {
		super();
	}

	public void init(IRegionBounds bounds) {
		init(bounds, true);
	}

	public void init(IRegionBounds bounds, boolean isNew) {
		this.bounds = bounds;
		regionDisplay = new IRegionDisplay(InformaCam.getInstance().a, this);

		if(isNew) {
			this.bounds.calculate();
			InformaCam.getInstance().informaService.addRegion(this);
		}
	}

	public IRegionDisplay getRegionDisplay() {
		return regionDisplay;
	}

	public void update() {
		bounds.calculate();
		InformaCam.getInstance().informaService.updateRegion(this);
	}

	public void delete(IMedia parent) {
		InformaCam.getInstance().informaService.removeRegion(this);
		parent.associatedRegions.remove(this);
	}
}
