package org.witness.informacam.transport;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.utils.Constants.Models;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class Transport extends Activity {
	ITransportStub transportStub;
	
	protected final static String LOG = "************************** TRANSPORT **************************";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			transportStub = new ITransportStub((JSONObject) new JSONTokener(getIntent().getStringExtra(Models.ITransportStub.TAG)).nextValue());
			Log.d(LOG, "TRANSPORT:\n" + transportStub.asJson().toString()); 
			if(transportStub == null) {
				finish();
			} else {
				init();
			}
		} catch(JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			finish();
		}
	}
	
	protected void init() {}
	protected void send() {}
	
	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {}
	
	protected void finishSuccessfully() {
		transportStub.resultCode = Models.ITransportStub.ResultCodes.OK;
		
		if(transportStub.associatedNotification != null) {
			transportStub.associatedNotification.taskComplete = true;
			InformaCam.getInstance().updateNotification(transportStub.associatedNotification);
		}
		
		finish();
	}
}
