package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.utils.IRegionDisplay;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

import android.app.Activity;
import android.content.Context;

public class IRegion extends Model {
	public String id = null;
	public long timestamp = 0L;

	public String formNamespace = null;
	public String formPath = null;

	public IRegionBounds bounds = null;
	private IRegionDisplay regionDisplay = null;
	
	private IRegionDisplayListener mListener;
	
	public IRegion() {
		super();
	}

	public void init(Activity context, IRegionBounds bounds, IRegionDisplayListener listener) {
		init(context, bounds, true, listener);
	}

	public void init(Activity context, IRegionBounds bounds, boolean isNew, IRegionDisplayListener listener) {
		
		this.bounds = bounds;
		mListener = listener;
		
		regionDisplay = new IRegionDisplay(context, this, mListener);

		if(isNew) {
			if(mListener != null) {
				this.bounds.calculate(mListener.getSpecs(),context);
			}
			InformaCam.getInstance().informaService.addRegion(this);
		}
	}

	public IRegionDisplay getRegionDisplay() {
		return regionDisplay;
	}

	public void update(Activity a) {
		InformaCam informaCam = InformaCam.getInstance();
		
		if(mListener != null) {
			bounds.calculate(mListener.getSpecs(), a);
		}
		
		informaCam.informaService.updateRegion(this);
	}

	public void delete(IMedia parent) {
		InformaCam.getInstance().informaService.removeRegion(this);
		parent.associatedRegions.remove(this);
	}
}
