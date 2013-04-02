package org.witness.informacam.models.connections;

import java.util.ArrayList;

import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.utils.Constants.Models;

import android.util.Base64;

public class IMessage extends IConnection {
	public IMessage(IOrganization organization, String messageText) {
		super();
		
		url = organization.requestUrl + Models.IConnection.Routes.MESSAGES;
		
		params = new ArrayList<IParam>();
		IParam param = new IParam();
		param.key = Models._ID;
		param.value = Models._REV;
		params.add(param);
		
		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_TO;
		param.value = organization.organizationName;
		params.add(param);
		
		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_TIME;
		param.value = System.currentTimeMillis();
		params.add(param);
		
		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_CONTENT;
		param.value = Base64.encodeToString(messageText.getBytes(), Base64.DEFAULT);
		params.add(param);
		
	}
}
