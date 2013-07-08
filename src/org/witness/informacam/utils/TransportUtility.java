package org.witness.informacam.utils;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.transport.DriveTransport;
import org.witness.informacam.utils.Constants.Models;

import android.content.Intent;

public class TransportUtility {
	
	public static void initTransport(ITransportStub transportStub) {
		InformaCam informaCam = InformaCam.getInstance();
		List<Intent> intents = new ArrayList<Intent>();
		
		for(IRepository repository : transportStub.organization.repositories) {
			
			Intent intent = null;
			if(repository.source.equals(Models.ITransportStub.RepositorySources.GOOGLE_DRIVE)) {
				intent = new Intent(informaCam, DriveTransport.class);
			}
			
			if(intent != null) {
				intent.putExtra(Models.ITransportStub.TAG, transportStub);
				intents.add(intent);
			}
		}
		
		if(!intents.isEmpty()) {
			informaCam.startActivities(intents.toArray(new Intent[intents.size()]));
		}
	}
}
