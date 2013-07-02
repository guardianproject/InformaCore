package org.witness.informacam.models.connections;

import java.util.ArrayList;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.annotation.SuppressLint;
import android.net.ConnectivityManager;
import android.util.Log;

public class IUpload extends IConnection {
	public int lastByte = -1;
	public int byteBufferSize = 0;
	
	public IUpload() {
		super();
	}
	
	public IUpload(Object connection) {
		super();
		inflate(((Model) connection).asJson());
	}
	
	public IUpload(IOrganization organization, String pathToData, String uploadId, String uploadRev) {
		super();
		destination = organization;
		
		type = Models.IConnection.Type.UPLOAD;
		port = organization.requestPort;
		
		data = new ITransportData();
		data.entityName = pathToData;
		data.key = "file";
		data.totalBytes = InformaCam.getInstance().ioService.getBytes(pathToData, Type.IOCIPHER).length;
		
		byteBufferSize = data.totalBytes;
		
		params = new ArrayList<IParam>();

		IParam param = new IParam();
		param.key = Models.IConnection.BELONGS_TO_USER;
		param.value = organization.identity._id;
		params.add(param);

		url = organization.requestUrl + Models.IConnection.Routes.UPLOAD + uploadId;
		method = Models.IConnection.Methods.POST;
		isSticky = true;
		
		update();
	}
	
	@SuppressLint("DefaultLocale")
	public void update() {
		setByteRange();
		
		String newByteRange = String.format("[%d,%d]", data.byteRange[0], data.byteRange[1]);
		boolean hadByteRange = false;
		
		for(IParam p : params) {
			if(p.key.equals(Models.IConnection.BYTE_RANGE)) {
				p.value = newByteRange;
				hadByteRange = true;
				break;
			}
		}
		
		if(!hadByteRange) {
			IParam param = new IParam();
			param.key = Models.IConnection.BYTE_RANGE;
			param.value = newByteRange;
			params.add(param);
		}
		
				
		Log.d(LOG, "updating upload ticket:\n" + this.asJson().toString());
	}
	
	public void setByteBufferSize(int connectionType) {
		Log.d(LOG, "connection type is " + connectionType);
		
		switch(connectionType) {
		case ConnectivityManager.TYPE_MOBILE:
			byteBufferSize = (int) 2000000L;	// 2mb
			break;
		case ConnectivityManager.TYPE_WIFI:
			byteBufferSize = (int) 5000000L;	// 5mb
			break;
		}
		
		// hey guess what: just take care of the whole thing before fretting about opportunistic upload oK?
		byteBufferSize = data.totalBytes;
		data.isWholeUpload = true;
		
		Log.d(LOG, "setting byte buffer size to " + byteBufferSize);
	}
	
	private void setByteRange() {
		int bytesLeft = data.totalBytes;
		if(lastByte != -1) {
			bytesLeft = Math.abs(data.totalBytes - lastByte);
		}
		
		data.byteRange[0] = (lastByte + 1);
		data.byteRange[1] = (data.byteRange[0] + (bytesLeft < byteBufferSize ? bytesLeft : byteBufferSize));
		
		Log.d(LOG, "SETTING BYTE RANGE ANEW: " + data.byteRange[0] + " - " + data.byteRange[1]);
	}

}