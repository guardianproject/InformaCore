package org.witness.informacam.models.utils;

import java.io.Serializable;

import org.witness.informacam.models.Model;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.utils.Constants.Models;

@SuppressWarnings("serial")
public class ITransportStub extends Model implements Serializable {
	public INotification associatedNotification = null;
	public IOrganization organization = null;
	public String assetPath = null;
	public String assetName = null;
	public String mimeType = null;
	
	public int resultCode = Models.ITransportStub.ResultCodes.FAIL;
	
	public ITransportStub() {
		super();
	}
	
	public ITransportStub(String assetPath, IOrganization organization, INotification associatedNotification) {
		this.assetPath = assetPath;
		this.organization = organization;
		this.associatedNotification = associatedNotification;
	}
	
	public String getAssetRootOfRepository(String source) {
		for(IRepository repository : organization.repositories) {
			if(repository.source.equals(source)) {
				return repository.asset_root;
			}
		}
		
		return null;
	}
}