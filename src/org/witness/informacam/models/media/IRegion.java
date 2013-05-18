package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.ui.IRegionDisplay;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

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
		InformaCam informaCam = InformaCam.getInstance();
		
		this.bounds = bounds;
		regionDisplay = new IRegionDisplay(informaCam.a, this);

		if(isNew) {
			this.bounds.calculate(((IRegionDisplayListener) informaCam.a).getSpecs());
			InformaCam.getInstance().informaService.addRegion(this);
		}
	}

	public IRegionDisplay getRegionDisplay() {
		return regionDisplay;
	}

	public void update() {
		InformaCam informaCam = InformaCam.getInstance();
		
		bounds.calculate(((IRegionDisplayListener) informaCam.a).getSpecs());
		informaCam.informaService.updateRegion(this);
	}

	public void delete(IMedia parent) {
		InformaCam.getInstance().informaService.removeRegion(this);
		parent.associatedRegions.remove(this);
	}
}
