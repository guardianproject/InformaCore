package org.witness.informacam.utils;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.transport.GlobaleaksTransport;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Transport;

import android.content.Intent;

public class TransportUtility {
	private static final String LOG = Transport.LOG;
	
	public static void initTransport(ITransportStub transportStub) {
		InformaCam informaCam = InformaCam.getInstance();
		Logger.d(LOG, "TRANSPORT:\n" + transportStub.asJson().toString());
		
		for(IRepository repository : transportStub.organization.repositories) {
			
			Intent intent = null;
			if(repository.source.equals(Models.ITransportStub.RepositorySources.GOOGLE_DRIVE)) {
				// TODO: via HTTP API
			}
			
			if(repository.source.equals(Models.ITransportStub.RepositorySources.GLOBALEAKS)) {
				intent = new Intent(informaCam, GlobaleaksTransport.class);
			}
			
			if(intent != null) {
				Logger.d(LOG, "HEY STARTING TO TRANSPORT");
				
				intent.putExtra(Models.ITransportStub.TAG, transportStub);
				informaCam.startService(intent);
			}
			
		}
		
		
	}
}
