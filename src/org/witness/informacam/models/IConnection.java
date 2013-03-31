package org.witness.informacam.models;

import info.guardianproject.iocipher.File;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class IConnection extends Model {
	public long _id = -1L;
	public int numTries = 0;
	public boolean isHeld = true;
	public String url = null;
	public int port = 443;
	public String method = Models.IConnection.Methods.GET;
	public ITransportCredentials transportCredentials = null;
	public IResult result = null;

	public List<IParam> params = null;
	public List<ITransportData> data = null;

	public IConnection() {
		_id = System.currentTimeMillis();
	}

	public void setParam(String key, Object value) {
		if(params == null) {
			params = new ArrayList<IParam>();
		}

		IParam param = new IParam();
		param.key = key;
		param.value = value;

		params.add(param);
	}

	public void setData(String key, String entityName, int source) {
		if(data == null) {
			data = new ArrayList<ITransportData>();
		}

		ITransportData data = new ITransportData();
		data.entityName = entityName;
		data.key = key;
		data.source = source;

		this.data.add(data);
	}

	public void setData(String key, String entityName) {
		setData(key, entityName, Type.IOCIPHER);
	}
	
	private void addData(HttpParams httpParams) {
		if(params != null && params.size() > 0) {
			for(IParam p : params) {
				httpParams.setParameter(p.key, p.value);
			}
		}
	}
	
	public HttpGet build(HttpGet get) {
		addData(get.getParams());
		return get;
	}

	public HttpPost build(HttpPost post) {
		InformaCam informaCam = InformaCam.getInstance();
		addData(post.getParams());
		
		if(data != null && data.size() > 0) {
			MultipartEntity entity = new MultipartEntity();
			for(ITransportData d : data) {
				ContentBody contentBody = new ByteArrayBody(informaCam.ioService.getBytes(d.entityName, d.source), d.entityName);
				entity.addPart(d.key, contentBody);
			}
			post.setEntity(entity);
		}
		
		return post;
	}
}
