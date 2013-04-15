package org.witness.informacam.models.connections;

import java.util.ArrayList;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.util.Base64;

public class IUpload extends IConnection {
	byte[] fileBytes;
	int[] byteRange = new int[2];
	
	public int lastByte = -1;
	public int byteBufferSize = 15000;
	
	public IUpload(IOrganization organization, String pathToData, String uploadId, String uploadRev) {
		super();
		
		fileBytes = InformaCam.getInstance().ioService.getBytes(pathToData, Type.IOCIPHER);
		setByteRange();
		
		this.params = new ArrayList<IParam>();

		IParam param = new IParam();
		param.key = Models.IConnection.BELONGS_TO_USER;
		param.value = organization.transportCredentials.userId;
		params.add(param);

		param = new IParam();
		param.key = Models.IConnection.BYTE_RANGE;
		param.value = String.format("%d,%d", byteRange[0], byteRange[1]);
		params.add(param);
		
		param = new IParam();
		param.key = Models.IConnection.DATA;
		param.value = Base64.encode(fileBytes, Base64.DEFAULT);
		params.add(param);

		this.url = organization.requestUrl + "upload/" + uploadId;
		this.method = Models.IConnection.Methods.POST;
		this.isSticky = true;
	}
	
	public void setProgress() {
		lastByte = byteRange[1];
	}
	
	private void setByteRange() {
		byteRange[0] = (lastByte + 1);
		byteRange[1] = (byteRange[0] + byteBufferSize);
	}

}
