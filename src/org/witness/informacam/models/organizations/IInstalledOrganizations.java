package org.witness.informacam.models.organizations;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.models.Model;

public class IInstalledOrganizations extends Model {
	public List<IOrganization> organizations = new ArrayList<IOrganization>();
	
	public List<IOrganization> listOrganizations() {
		return organizations;
	}
	
	public IOrganization getByName(String organizationName) {
		for(IOrganization o : organizations) {
			if(o.organizationName.equals(organizationName)) {
				return o;
			}
		}
		
		return null;
	}
	
}
