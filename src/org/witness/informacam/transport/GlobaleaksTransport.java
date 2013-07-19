package org.witness.informacam.transport;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;

public class GlobaleaksTransport extends Transport {
	GLSubmission submission = null;
	
	public GlobaleaksTransport() {
		super(Models.ITransportStub.Globaleaks.TAG);
	}
	
	@Override
	protected void init() {
		super.init();
		
		submission = new GLSubmission();
		submission.context_gus = repository.asset_id;
		
		transportStub.asset.key = "files[]";	// (?)
		
		Logger.d(LOG, submission.asJson().toString());
		
		// init submission
		if(doPost(submission, repository.asset_root + "/submission") != null) {
			// upload file
			if(doPost(transportStub.asset, repository.asset_root + "/submission/" + submission.submission_gus + "/file") != null) {
				// update submission
				doPut(submission, repository.asset_root + "/submission/" + submission.submission_gus);
			}
		}
		
		
		/*
		 * 1) post to
	url/submission
	with:
	
	{
			"context_gus":"0a59eb17-f80a-46bd-884e-0bd407948ddb",
			"wb_fields":{},
			"files":[],
			"finalize":false,
			"receivers":[]
	}
	
	returns:
	
	{
			"wb_fields": {}, 
			"pertinence": "0", 
			"receivers": [], 
			"expiration_date": "2013-07-30T13:33:40.815188", 
			"access_limit": 50, "receipt": "", 
			"context_gus": "0a59eb17-f80a-46bd-884e-0bd407948ddb", 
			"creation_date": "2013-07-15T13:33:40.815232", 
			"escalation_threshold": "0", 
			"download_limit": 3, 
			"submission_gus": "44324b37-385c-44cd-b902-c09dd3636e63", 
			"mark": "submission", 
			"id": "44324b37-385c-44cd-b902-c09dd3636e63", 
			"files": []
	}
	
2) post to
	url/submission/submission_gus/file
	with:

	(just post the data)
	
	returns:
	
	[
		{
			"name": "1373890950660_20130715_081215.jpg", 
			"creation_date": "2013-07-15T13:35:01.012312", 
			"elapsed_time": 0.07422018051147461, 
			"content_type": "image/jpeg", 
			"mark": "not processed", 
			"id": "3f3bad61-6fa9-4db9-bedc-730ddbb9acf7", 
			"size": 3324831
		}
	]
	
3) put to
	url/submission/context_gus
	with:
	
	{
		"wb_fields":{
			"Full description":"",
			"Files description":"",
			"Short title":""
		},
		"pertinence":"0",
		"receivers":[
			"fd0248fe-1c2b-4936-b8ab-ee6e6162396b",
			"c350c638-ee21-4189-8b70-dc28d27a8518",
			"de9d98a3-3b9b-469c-ae78-931d518a20b1",
			"961db853-319d-417a-ba8b-c844141ffcfa"
		],
		"expiration_date":"2013-07-30T13:33:40.815188",
		"access_limit":50,
		"receipt":"",
		"context_gus":"0a59eb17-f80a-46bd-884e-0bd407948ddb",
		"creation_date":"2013-07-15T13:33:40.815232",
		"escalation_threshold":"0",
		"download_limit":3,
		"submission_gus":"44324b37-385c-44cd-b902-c09dd3636e63",
		"mark":"submission",
		"id":"44324b37-385c-44cd-b902-c09dd3636e63",
		"files":[],
		"finalize":true
	}
	
	receive:
	
	{
		"error_message": "Submission do not validate the input fields [Missing field 'Short title': Required]", 
		"error_code": 22, 
		"arguments": [
			"admin",
			300,
			"admin",
			300, 
			"admin", 
			300, 
			"admin", 
			300, 
			"admin", 
			300, 
			"Missing field 'Short title': Required"
		]
	}
		 */
	}
	
	@Override
	public Object parseResponse(InputStream response) {
		super.parseResponse(response);
		
		Logger.d(LOG, "PARSING RESULT AS I DO");
		
		return null;
	}
	
	@SuppressWarnings("serial")
	public class GLSubmission extends Model implements Serializable {
		public String context_gus = null;
		public String submission_gus = null;
		public boolean finalize = false;
		public List<String> files = new ArrayList<String>();
		public List<String> receivers = new ArrayList<String>();
		public JSONObject wb_fields = new JSONObject();
		public String pertinence = null;
		public String expiration_date = null;
		public String creation_date = null;
		
		public GLSubmission() {
			super();
		}
	}

}
