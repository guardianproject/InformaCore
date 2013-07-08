package org.witness.informacam.transport;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.utils.Constants.Models;

import android.app.Activity;
import android.os.Bundle;

public class Transport extends Activity {
	ITransportStub transportStub;
	
	protected final static String LOG = "************************** TRANSPORT **************************";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		transportStub = (ITransportStub) getIntent().getSerializableExtra(Models.ITransportStub.TAG);
		if(transportStub == null) {
			finish();
		}
	}
	
	protected void init() {}
	protected void send() {}
	
	protected void finishSuccessfully() {
		transportStub.resultCode = Models.ITransportStub.ResultCodes.OK;
		
		if(transportStub.associatedNotification != null) {
			transportStub.associatedNotification.taskComplete = true;
			InformaCam.getInstance().updateNotification(transportStub.associatedNotification);
		}
		
		finish();
	}
}
