package org.witness.informacam.models.notifications;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.util.Log;

public class INotification extends Model {
	public long timestamp = 0L;
	public String label = null;
	public String content = null;
	public String from = null;
	public String icon = null;
	public int iconSource = Type.IOCIPHER;
	public int type = 0;
	public String _id = null;
	public boolean taskComplete = true;
	
	public INotification() {
		super();
		this.timestamp = System.currentTimeMillis();
	}
	
	public INotification(String label, String content, int type) {
		this.label = label;
		this.content = content;
		this.type = type;
		this.timestamp = System.currentTimeMillis();
		
		generateId();
	}
	
	public boolean delete() {
		InformaCam informaCam = InformaCam.getInstance();
		try {
			informaCam.notificationsManifest.notifications.remove(this);
			informaCam.saveState(informaCam.notificationsManifest);
			return true;
		} catch(NullPointerException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public void generateId() {
		StringBuffer sb = new StringBuffer();
		
		JSONObject json = asJson();
		Iterator<String> keys = json.keys();
		while(keys.hasNext()) {
			String key = keys.next();
			try {
				sb.append(String.valueOf(json.get(key)));
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		try {
			this._id = MediaHasher.hash(sb.toString().getBytes(), "MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		this._id = String.valueOf(timestamp);
	}

	public void save() {
		InformaCam informaCam = InformaCam.getInstance();
		informaCam.notificationsManifest.getById(this._id).inflate(asJson());
		informaCam.notificationsManifest.save();
		
	}
}
