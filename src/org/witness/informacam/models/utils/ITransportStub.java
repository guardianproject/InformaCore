package org.witness.informacam.models.utils;

import java.io.Serializable;

import org.witness.informacam.models.Model;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;

@SuppressWarnings("serial")
public class ITransportStub extends Model implements Serializable {
	public INotification associatedNotification = null;
	public IOrganization organization = null;
	public String assetPath = null;
	
	public ITransportStub() {
		super();
	}
	
	public ITransportStub(String assetPath, IOrganization organization, INotification associatedNotification) {
		this.assetPath = assetPath;
		this.organization = organization;
		this.associatedNotification = associatedNotification;
		
	}
}