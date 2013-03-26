package org.witness.informacam.utils.models;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models;

public class IConnection extends Model {
	public long _id = -1L;
	public boolean isHeld = true;
	public String url = null;
	public String method = Models.IConnection.Methods.GET;
	public IResult result = null;

	public List<IParam> params = null;
	public List<IData> data = null;

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
			data = new ArrayList<IData>();
		}

		IData data = new IData();
		data.entityName = entityName;
		data.key = key;
		data.source = source;

		this.data.add(data);
	}

	public void setData(String key, String entityName) {
		setData(key, entityName, Type.IOCIPHER);
	}

	public class IResult extends Model {
		public int code = 0;
		public String reason = null;
		public JSONObject data = null;
		
		public IResult(int code, JSONObject data) {
			this.code = code;
			this.data = data;
		}
		
		public IResult(int code, String reason) {
			this.code = code;
			this.reason = reason;
		}

	}
}
