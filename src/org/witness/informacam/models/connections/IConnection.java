package org.witness.informacam.models.connections;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.IParam;
import org.witness.informacam.models.IResult;
import org.witness.informacam.models.ITransportCredentials;
import org.witness.informacam.models.ITransportData;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models;

import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.ByteArrayBody;
import ch.boye.httpclientandroidlib.entity.mime.content.ContentBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;

import android.util.Log;

public class IConnection extends Model {
	public long _id = -1L;
	public int numTries = 0;
	public boolean isHeld = false;
	public boolean isSticky = false;
	public String url = null;
	public int port = 443;
	public int type = Models.IConnection.Type.NONE;
	public IOrganization destination = null;
	public String method = Models.IConnection.Methods.POST;
	public ITransportCredentials transportCredentials = null;
	public IResult result = null;
	public int knownCallback = 0; 
	
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
	
	private List<NameValuePair> addData() {
		if(params != null && params.size() > 0) {
			List<NameValuePair> params_ = new ArrayList<NameValuePair>();
			for(IParam p : params) {
				Log.d(LOG, "setting data: " + p.key + ": " + p.value);
				params_.add(new BasicNameValuePair(p.key, String.valueOf(p.value)));
			}
			return params_;
		}
		
		return null;
	}
	
	public String build() {
		// for get requests
		StringBuilder get = new StringBuilder().append(url);
		List<NameValuePair> params_ = addData();
		if(params_ != null) {
			get.append("?");
			get.append(URLEncodedUtils.format(params_, "utf-8"));
		}
		
		return get.toString();
	}

	public HttpPost build(HttpPost post) {
		InformaCam informaCam = InformaCam.getInstance();		
		
		if(data != null && data.size() > 0) {
			MultipartEntity entity = new MultipartEntity();
			
			try {
				List<NameValuePair> params_ = addData();
				if(params_ != null) {
					for(NameValuePair p : params_) {
						entity.addPart(p.getName(), new StringBody(p.getValue()));
					}
					
					Log.d(LOG, "so i set these entities apparently");
				}
			} catch (UnsupportedEncodingException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			
			for(ITransportData d : data) {
				Log.d(LOG, "adding data: " + d.entityName);
				ContentBody contentBody = new ByteArrayBody(informaCam.ioService.getBytes(d.entityName, d.source), d.entityName);
				entity.addPart(d.key, contentBody);
			}
			post.setEntity((HttpEntity) entity);
		}
		
		return post;
	}
}