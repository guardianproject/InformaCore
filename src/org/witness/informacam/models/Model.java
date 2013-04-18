package org.witness.informacam.models;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App;
import android.util.Log;

public class Model extends JSONObject {
	public final static String LOG = App.LOG;
	Field[] fields;

	@SuppressWarnings("rawtypes")
	public Object getObjectByParameter(List root, String key, Object value) {
		for(Object o : root) {
			try {
				Field f = o.getClass().getDeclaredField(key);
				Object v = f.get(o);

				if(v.equals(value)) {
					return o;
				}
			} catch (NoSuchFieldException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		return null;
	}

	public void inflate(byte[] jsonStringBytes) {
		try {
			if(jsonStringBytes != null) {
				inflate((JSONObject) new JSONTokener(new String(jsonStringBytes)).nextValue());
			} else {
				Log.d(LOG, "json is null, no inflate");
			}
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	private Class<?> recast(Object m, JSONObject ja) {
		InformaCam informaCam = InformaCam.getInstance();
		
		List<Class<?>> subclasses = new ArrayList<Class<?>>();
		Class<?> clz = m.getClass();
		Class<?> recast = null;
		
		String packagePath = clz.getName().replace(("." + clz.getSimpleName()), "");
		
		for(String model : informaCam.models) {
			if(model.contains(packagePath) && !model.equals(clz.getName())) {
				try {
					Class<?> subClz = Class.forName(model);
					if(subClz.getSuperclass().equals(clz)) {
						//Log.d(LOG, "adding " + model + " as possible subclass for " + clz.getName());
						subclasses.add(subClz);
					}
				} catch (ClassNotFoundException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
				
			}
		}
		
		if(subclasses.size() > 0) {
			List<Class<?>> subClz_ = new ArrayList<Class<?>>(subclasses);
			// loop through json to see if we have any of these fields. eliminate non-matches from list
			for(Class<?> c : subclasses) {
				try {
					Object o = c.newInstance();
					
					for(Field subField : o.getClass().getDeclaredFields()) {
						if(!ja.has(subField.getName())) {
							subClz_.remove(c);
							break;
						}
					}
					
				} catch (InstantiationException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
			}
			
			if(subClz_.size() == 1) {
				Log.d(LOG, "downcast object to " + subClz_.get(0).getName());
				recast = subClz_.get(0);
			}
		}
		
		return recast;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void inflate(JSONObject values) {
		fields = this.getClass().getFields();

		for(Field f : fields) {
			try {
				f.setAccessible(true);
				if(values.has(f.getName())) {
					boolean isModel = false;

					if(f.getType().getSuperclass() == Model.class) {
						isModel = true;
					}					

					if(f.getType() == List.class) {
						List subValue = new ArrayList();

						Class clz = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];

						Object test = clz.newInstance();
						if(test instanceof Model) {
							isModel = true;
						}

						JSONArray ja = values.getJSONArray(f.getName());
						for(int i=0; i<ja.length(); i++) {
							Object value = clz.newInstance();
							if(isModel) {
								Class<?> recast = recast(value, ja.getJSONObject(i));
								if(recast != null) {
									value = recast.newInstance();
								}
								
								((Model) value).inflate(ja.getJSONObject(i));
							} else {
								value = ja.get(i);
							}
							subValue.add(value);
						}

						f.set(this, subValue);
					} else if(f.getType() == byte[].class) { 
						f.set(this, values.getString(f.getName()).getBytes());
					} else if(f.getType() == float[].class) {
						f.set(this, parseJSONAsFloatArray(values.getString(f.getName())));
					} else if(isModel) {						
						Class clz = (Class<?>) f.getType();
						// if clz has less fields than the json object, this could be a subclass
						
						Object val = clz.newInstance();
						Class<?> recast = recast(val, values.getJSONObject(f.getName()));
						if(recast  != null) {
							val = recast.newInstance();
						}

						((Model) val).inflate(values.getJSONObject(f.getName()));
						f.set(this, val);
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

	public static float[] parseJSONAsFloatArray(String value) {
		String[] floatStrings = value.substring(1, value.length() - 1).split(",");
		float[] floats = new float[floatStrings.length];

		for(int f=0; f<floatStrings.length; f++) {
			floats[f] = Float.parseFloat(floatStrings[f]);
		}

		return floats;
	}

	public String parseFloatArrayAsJSON(float[] floats) {
		StringBuffer floatString = new StringBuffer();
		for(float f : floats) {
			floatString.append("," + f);
		}

		return "[" + floatString.toString().substring(1) + "]";
	}

	public JSONObject asJson() {
		fields = this.getClass().getFields();
		JSONObject json = new JSONObject();

		for(Field f : fields) {
			f.setAccessible(true);

			try {
				Object value = f.get(this);
				//Log.d(LOG, "HEY THIS TYPE " + f.getType().getSuperclass());

				if(f.getName().contains("this$")) {
					continue;
				}

				if(f.getName().equals("NULL") || f.getName().equals("LOG")) {
					continue;
				}

				boolean isModel = false;

				if(f.getType().getSuperclass() == Model.class) {
					isModel = true;
				}

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
				} else if(f.getType() == float[].class) {
					json.put(f.getName(), parseFloatArrayAsJSON((float[]) value));
				} else if(isModel) {
					json.put(f.getName(), ((Model) value).asJson());
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

			}

		}

		return json;
	}

}
