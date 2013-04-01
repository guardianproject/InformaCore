package org.witness.informacam.models;

import java.util.List;

public class IInstalledOrganizations extends Model {
	public List<IOrganization> organizations;
	
	public IOrganization getByName(String organizationName) {
		for(IOrganization o : organizations) {
			if(o.organizationName.equals(organizationName)) {
				return o;
			}
		}
		
		return null;
	}
	
}
