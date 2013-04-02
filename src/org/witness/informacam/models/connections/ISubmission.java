package org.witness.informacam.models.connections;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Models;

public class ISubmission extends IConnection {
	JSONObject j3mDescriptor = null;
	
	public ISubmission(IOrganization organization) {
		super();
		
		this.params = new ArrayList<IParam>();
		
		IParam param = new IParam();
		param.key = Models._ID;
		param.value = organization.transportCredentials.userId;
		params.add(param);
		
		param = new IParam();
		param.key = Models._REV;
		param.value = organization.transportCredentials.userRev;
		params.add(param);
		
		this.url = organization.requestUrl + "submissions/";
		this.method = Models.IConnection.Methods.POST;
		this.knownCallback = Models.IResult.ResponseCodes.UPLOAD_SUBMISSION;
	}
	
	public void Set(info.guardianproject.iocipher.File mediaFile) {
		InformaCam informaCam = InformaCam.getInstance();
		try {
			j3mDescriptor = new JSONObject();
			j3mDescriptor.put(Models.IMedia.j3m.SIZE, mediaFile.length());
			j3mDescriptor.put(Models.IMedia.j3m.HASH, MediaHasher.hash(informaCam.ioService.getBytes(mediaFile.getAbsolutePath(), Type.IOCIPHER), "SHA-1"));
			
			IParam param = new IParam();
			param.key = Models.IMedia.J3M_DESCRIPTOR;
			param.value = j3mDescriptor;
			params.add(param);
			
			isHeld = false;
			informaCam.uploaderService.addToQueue(this);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
