package org.witness.informacam.models.connections;

import java.util.ArrayList;

import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.Models;

import android.util.Base64;

public class IMessage extends IConnection {
	public String messageTo = null;
	public String messageContent = null;
	
	public IMessage() {
		super();
	}
	
	public IMessage(IOrganization organization, String messageText) {
		super();
		destination = organization;
		
		type = Models.IConnection.Type.MESSAGE;
		url = organization.requestUrl + Models.IConnection.Routes.MESSAGES;
		port = organization.requestPort;
		
		messageTo = organization.organizationName;
		messageContent = Base64.encodeToString(messageText.getBytes(), Base64.DEFAULT);

		params = new ArrayList<IParam>();
		IParam param = new IParam();
		param.key = Models._ID;
		param.value = Models._REV;
		params.add(param);

		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_TO;
		param.value = messageTo;
		params.add(param);

		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_TIME;
		param.value = System.currentTimeMillis();
		params.add(param);

		param = new IParam();
		param.key = Models.IConnection.CommonParams.MESSAGE_CONTENT;
		param.value = messageContent;
		params.add(param);

	}
}
