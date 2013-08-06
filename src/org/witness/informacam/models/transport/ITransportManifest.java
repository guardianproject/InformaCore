package org.witness.informacam.models.transport;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Logger;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class ITransportManifest extends Model implements Serializable {
	public List<ITransportStub> transports = null;
	
	private static final long serialVersionUID = -4261623918639178561L;
	
	public ITransportManifest() {
		super();
	}
	
	public ITransportManifest(ITransportManifest transportManifest) {
		super();
		inflate(transportManifest);
	}
	
	public void add(ITransportStub transportStub) {
		if(getById(transportStub.id) == null) {
			transports.add(transportStub);
		} else {
			getById(transportStub.id).inflate(transportStub);
		}
		
		InformaCam.getInstance().saveState(this);
	}
	
	public ITransportStub getByNotification(final String id) {
		Collection<ITransportStub> transports_ = Collections2.filter(transports, new Predicate<ITransportStub>() {
			@Override
			public boolean apply(ITransportStub transport) {
				if(transport.associatedNotification != null) {
					return transport.associatedNotification._id.equals(id);
				} else {
					return false;
				}
			}
		});
		
		try {
			return transports_.iterator().next();
		} catch(NullPointerException e) {
			Logger.e(LOG, e);
			return null;
		}
	}
	
	public ITransportStub getById(final String id) {
		Collection<ITransportStub> transports_ = Collections2.filter(transports, new Predicate<ITransportStub>() {
			@Override
			public boolean apply(ITransportStub transport) {
				return transport.id.equals(id);
			}
		});
		
		try {
			return transports_.iterator().next();
		} catch(NullPointerException e) {
			Logger.e(LOG, e);
			return null;
		}
	}

}
