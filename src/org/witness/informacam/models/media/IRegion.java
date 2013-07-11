package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.utils.IRegionDisplay;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;

import android.app.Activity;

public class IRegion extends Model {
	public String id = null;
	public long timestamp = 0L;

	public List<IForm> associatedForms = new ArrayList<IForm>();

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
	
	public IForm getFormByNamespace(String namespace) {
		return getFormsByNamespace(namespace).get(0);
	}
	
	public List<IForm> getFormsByNamespace(String namespace) {
		List<IForm> forms = new ArrayList<IForm>();
		for(IForm form : this.associatedForms) {
			if(form.namespace.equals(namespace)) {
				forms.add(form);
			}
		}
		return forms;
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
