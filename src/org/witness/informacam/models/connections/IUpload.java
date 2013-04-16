package org.witness.informacam.models.connections;

import java.util.ArrayList;

import org.bouncycastle.util.Arrays;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.net.ConnectivityManager;
import android.util.Base64;
import android.util.Log;

public class IUpload extends IConnection {
	byte[] fileBytes;
	int[] byteRange = new int[2];
	
	public int lastByte = -1;
	public int byteBufferSize = 0;
	public int totalBytes = 0;
	
	public IUpload(IOrganization organization, String pathToData, String uploadId, String uploadRev) {
		super();
		
		fileBytes = InformaCam.getInstance().ioService.getBytes(pathToData, Type.IOCIPHER);
		totalBytes = fileBytes.length;
		byteBufferSize = totalBytes;
		
		this.params = new ArrayList<IParam>();

		IParam param = new IParam();
		param.key = Models.IConnection.BELONGS_TO_USER;
		param.value = organization.transportCredentials.userId;
		params.add(param);

		this.url = organization.requestUrl + "upload/" + uploadId;
		this.method = Models.IConnection.Methods.POST;
		this.isSticky = true;
		
		update();
	}
	
	public void update() {
		setByteRange();
		lastByte = byteRange[1];
		
		for(IParam p : params) {
			if(p.key.equals(Models.IConnection.BYTE_RANGE)) {
				params.remove(p);
			}
			
			if(p.key.equals(Models.IConnection.DATA)){
				params.remove(p);
			}
		}
		
		IParam param = new IParam();
		param = new IParam();
		param.key = Models.IConnection.BYTE_RANGE;
		param.value = String.format("[%d,%d]", byteRange[0], byteRange[1]);
		params.add(param);
		
		param = new IParam();
		param.key = Models.IConnection.DATA;
		
		byte[] d = Arrays.copyOfRange(fileBytes, byteRange[0], byteRange[1]);
		Log.d(LOG, "sending chunk size " + d.length + " (" + byteRange[0] + " - " + byteRange[1] + ")");
		param.value = Base64.encode(d, Base64.DEFAULT);
		d = null;
		
		params.add(param);
		
		Log.d(LOG, "updating upload ticket:\n" + this.asJson().toString());
	}
	
	public void setByteBufferSize(int connectionType) {
		Log.d(LOG, "connection type is " + connectionType);
		switch(connectionType) {
		case ConnectivityManager.TYPE_MOBILE:
			byteBufferSize = (int) (fileBytes.length/50000L);
			break;
		case ConnectivityManager.TYPE_WIFI:
			byteBufferSize = (int) (fileBytes.length/100000L);
			break;
		case ConnectivityManager.TYPE_WIMAX:
			byteBufferSize = (int) (fileBytes.length/200000L);
			break;
		}
		
		Log.d(LOG, "setting byte buffer size to " + byteBufferSize);
		
	}
	
	private void setByteRange() {
		byteRange[0] = (lastByte + 1);
		byteRange[1] = (byteRange[0] + byteBufferSize);
	}

}
