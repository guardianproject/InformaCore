package org.witness.informacam.models.connections;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.models.Model;

import android.util.Log;

public class ISubmission extends IConnection {
	public String pathToNextConnectionData = null;
	
	public ISubmission() {
		super();
	}
	
	public ISubmission(Object connection) {
		super();
		inflate(((Model) connection).asJson());
	}
	
	public ISubmission(IOrganization organization, String pathToNextConnectionData) {
		super();
		
		destination = organization;
		type = Models.IConnection.Type.SUBMISSION;
		port = organization.requestPort;
		
		params = new ArrayList<IParam>();
		
		IParam param = new IParam();
		param.key = Models._ID;
		param.value = organization.identity._id;
		params.add(param);
		
		param = new IParam();
		param.key = Models._REV;
		param.value = organization.identity._rev;
		params.add(param);
		
		url = organization.requestUrl + Models.IConnection.Routes.SUBMISSIONS;
		knownCallback = Models.IResult.ResponseCodes.UPLOAD_SUBMISSION;
		
		this.pathToNextConnectionData = pathToNextConnectionData;
	}
	
	public void Set(info.guardianproject.iocipher.File mediaFile) {
		InformaCam informaCam = InformaCam.getInstance();
		try {
			JSONObject j3mDescriptor = new JSONObject();
			j3mDescriptor.put(Models.IMedia.j3m.SIZE, mediaFile.length());
			j3mDescriptor.put(Models.IMedia.j3m.HASH, MediaHasher.hash(informaCam.ioService.getBytes(mediaFile.getAbsolutePath(), Type.IOCIPHER), "SHA-1"));
			j3mDescriptor.put(Models.IMedia.j3m.FILE_NAME, mediaFile.getName());
			
			IParam param = new IParam();
			param.key = Models.IMedia.J3M_DESCRIPTOR;
			param.value = j3mDescriptor;
			
			if(params == null) {
				params = new ArrayList<IParam>();
			}
			
			params.add(param);
			
			isHeld = false;
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
	}
}