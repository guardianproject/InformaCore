package org.witness.informacam.models.connections;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Arrays;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.models.organizations.ITransportCredentials;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models;

import ch.boye.httpclientandroidlib.NameValuePair;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.client.utils.URLEncodedUtils;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.ByteArrayBody;
import ch.boye.httpclientandroidlib.entity.mime.content.ContentBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;
import ch.boye.httpclientandroidlib.message.BasicNameValuePair;

import android.util.Base64;
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
	public ITransportData data = null;

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

		data = new ITransportData();
		data.entityName = entityName;
		data.key = key;
		data.source = source;
	}

	public void setData(String key, String entityName) {
		setData(key, entityName, Type.IOCIPHER);
	}

	private List<NameValuePair> addParams() {
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
		List<NameValuePair> params_ = addParams();
		if(params_ != null) {
			get.append("?");
			get.append(URLEncodedUtils.format(params_, "utf-8"));
		}

		return get.toString();
	}
	
	private byte[] chunkData(ITransportData data) {
		InformaCam informaCam = InformaCam.getInstance();
		
		byte[] fileBytes = informaCam.ioService.getBytes(data.entityName, data.source);
		byte[] d = Arrays.copyOfRange(fileBytes, data.byteRange[0], data.byteRange[1]);
		Log.d(LOG, "sending chunk size " + d.length + " (" + data.byteRange[0] + " - " + data.byteRange[1] + ")");
		fileBytes = null;
		
		return d;
	}

	public HttpPost build(HttpPost post) {
		if(params != null && params.size() > 0) {
			MultipartEntity entity = new MultipartEntity();

			try {
				List<NameValuePair> params_ = addParams();
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

			if(data != null) {
				Log.d(LOG, "adding data: " + data.entityName);
				ContentBody contentBody = new ByteArrayBody(chunkData(data), data.entityName);
				entity.addPart(data.key, contentBody);

			}

			post.setEntity(entity);
		}

		return post;
	}
}