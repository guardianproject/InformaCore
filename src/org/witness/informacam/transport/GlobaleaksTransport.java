package org.witness.informacam.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
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
		
		transportStub.asset.key = "files";	// (?)
		
		Logger.d(LOG, submission.asJson().toString());
		
		// init submission
		try {
			submission.inflate((JSONObject) doPost(submission, repository.asset_root + "/submission"));
			
			if(submission.submission_gus != null) {
				if(doPost(transportStub.asset, repository.asset_root + "/submission/" + submission.submission_gus + "/file") != null) {
					submission.finalize = true;
					
					JSONArray receivers = (JSONArray) doGet(repository.asset_root + "/receivers");
					if(receivers != null) {
						if(receivers.length() > 0) {
							submission.receivers = new ArrayList<String>();

							for(int r=0; r<receivers.length(); r++) {
								try {
									JSONObject receiver = receivers.getJSONObject(r);
									submission.receivers.add(receiver.getString(GLSubmission.RECEIVER_GUS));
								} catch (JSONException e) {
									Logger.e(LOG, e);
								}
							}
						}
					} else {
						resend();
					}
					
					doPut(submission, repository.asset_root + "/submission/" + submission.submission_gus);
					finishSuccessfully();
				} else {
					resend();
				}
				
			}
			
		} catch(NullPointerException e) {
			Logger.e(LOG, e);
		}
	}
	
	@Override
	public Object parseResponse(InputStream response) {
		super.parseResponse(response);
		try {
			response.close();
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		if(transportStub.lastResult.charAt(0) == '[') {
			try {
				return (JSONArray) new JSONTokener(transportStub.lastResult).nextValue();
			} catch (JSONException e) {
				Logger.e(LOG, e);
			}
		} else {
			try {
				return (JSONObject) new JSONTokener(transportStub.lastResult).nextValue();
			} catch (JSONException e) {
				Logger.e(LOG, e);
			}
		}
		
		Logger.d(LOG, "THIS POST DID NOT WORK");
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
		public String receipt = null;
		public String escalation_threshold = null;
		public String mark = null;
		public String id = null;
		
		public String download_limit = null;
		public String access_limit = null;
		
		private final static String DOWNLOAD_LIMIT = "download_limit";
		private final static String ACCESS_LIMIT = "access_limit";
		private final static String RECEIVER_GUS = "receiver_gus";
		
		@Override
		public void inflate(JSONObject values) {
			try {
				if(values.has(DOWNLOAD_LIMIT)) {
					values = values.put(DOWNLOAD_LIMIT, Integer.toString(values.getInt(DOWNLOAD_LIMIT)));
				}
			} catch (JSONException e) {
				Logger.e(LOG, e);
			}
			
			try {
				if(values.has(ACCESS_LIMIT)) {
					values = values.put(ACCESS_LIMIT, Integer.toString(values.getInt(ACCESS_LIMIT)));
				}
			} catch (JSONException e) {
				Logger.e(LOG, e);
			}
			
			Logger.d(LOG, values.toString());
			super.inflate(values);
		}
		
		@Override
		public JSONObject asJson() {
			JSONObject obj = super.asJson();
			
			try {
				obj = obj.put(DOWNLOAD_LIMIT, Integer.parseInt(DOWNLOAD_LIMIT));
			} catch (NumberFormatException e) {}
			catch (JSONException e) {
				Logger.e(LOG, e);
			}
			
			try {
				obj = obj.put(ACCESS_LIMIT, Integer.parseInt(ACCESS_LIMIT));
			} catch (NumberFormatException e) {}
			catch (JSONException e) {
				Logger.e(LOG, e);
			}
			
			return obj;
		}
		
		
		public GLSubmission() {
			super();
		}
	}

}
