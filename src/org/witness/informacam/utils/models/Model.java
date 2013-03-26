package org.witness.informacam.utils.models;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.utils.Constants.App;

import android.util.Log;

public class Model extends JSONObject {
	public final static String LOG = App.LOG;
	Field[] fields;

	public void inflate(byte[] jsonStringBytes) {
		try {
			inflate((JSONObject) new JSONTokener(new String(jsonStringBytes)).nextValue());
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void inflate(JSONObject values) {
		//Log.d(LOG, "attempting to inflate object with values:\n" + values.toString());
		fields = this.getClass().getDeclaredFields();

		for(Field f : fields) {
			try {
				f.setAccessible(true);
				if(values.has(f.getName())) {
					if(f.getType() == List.class) {
						List subValue = new ArrayList();
						boolean isModel = false;

						Class clz = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
						Log.d(LOG, "UGH: " + clz.getName());

						Object test = clz.newInstance();
						if(test instanceof Model) {
							isModel = true;
						}

						if(isModel) {
							JSONArray ja = values.getJSONArray(f.getName());
							for(int i=0; i<ja.length(); i++) {
								Object value = clz.newInstance();
								((Model) value).inflate(ja.getJSONObject(i));
								subValue.add(value);
							}
						} else {
							for(Object v : (List<?>) values.get(f.getName())) {
								subValue.add(v);
							}
						}

						f.set(this, subValue);
					} else if(f.getType() == byte[].class) { 
						f.set(this, values.getString(f.getName()).getBytes());
					} else {
						f.set(this, values.get(f.getName()));
					}
				}
			} catch (IllegalArgumentException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (InstantiationException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} 
		}

		//Log.d(LOG, "finished inflating object, which is now\n" + this.asJson().toString());
	}

	public JSONObject asJson() {
		fields = this.getClass().getDeclaredFields();
		JSONObject json = new JSONObject();

		for(Field f : fields) {
			f.setAccessible(true);

			if(f.getName().contains("this$")) {
				continue;
			}

			try {
				Object value = f.get(this);
				if(f.getType() == List.class) {
					JSONArray subValue = new JSONArray();
					for(Object v : (List<?>) value) {
						// Log.d(LOG, v.getClass().getName());
						if(v instanceof Model) {
							subValue.put(((Model) v).asJson());
						} else {
							subValue.put(v);
						}
					}

					json.put(f.getName(), subValue);
				} else if(f.getType() == byte[].class) {
					json.put(f.getName(), new String((byte[]) value));
				} else {
					json.put(f.getName(), value);
				}
			} catch (IllegalArgumentException e) {
				Log.d(LOG, e.toString());
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				Log.d(LOG, e.toString());
				e.printStackTrace();
			} catch (JSONException e) {
				Log.d(LOG, e.toString());
				e.printStackTrace();
			} catch (NullPointerException e) {
				Log.d(LOG, e.toString());
				e.printStackTrace();
			}

		}

		return json;
	}

}
