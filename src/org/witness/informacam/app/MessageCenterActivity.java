package org.witness.informacam.app;

import java.util.HashMap;
import java.util.Map;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.j3m.J3M.J3MManifest;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class MessageCenterActivity extends Activity implements OnClickListener, OnRoutedListener {
	
	Handler h = new Handler();
	DatabaseHelper dh = DatabaseService.getInstance().getHelper();
	SQLiteDatabase db = DatabaseService.getInstance().getDb();
	
	ImageButton navigation;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initLayout();
		
		MainRouter.show(this);
	}
	
	private void initLayout() {
		setContentView(R.layout.messagecenteractivity);
		
		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);
	}
	
	private void parseMessages(JSONArray messages) {
		
	}
	
	private void getMessages() {
		dh.setTable(db, Tables.Keys.MEDIA);
		Cursor media = dh.getValue(db, new String[] {Media.Keys.J3M_MANIFEST}, null, null);
		if(media != null && media.moveToFirst()) {
			while(!media.isAfterLast()) {
				byte[] jmd = null;
				try {
					jmd = media.getBlob(media.getColumnIndex(Media.Keys.J3M_MANIFEST));
					Log.d(Transport.LOG, new String(jmd));
				} catch(Exception e) {
					media.moveToNext();
					continue;
				}
				
				try {
					J3MManifest j3mManifest = new J3MManifest((JSONObject) new JSONTokener(new String(jmd)).nextValue());
					if(j3mManifest.has(Manifest.UPLOADED_FLAG) && j3mManifest.getBoolean(Manifest.UPLOADED_FLAG)) {
						Log.e(App.LOG, j3mManifest.toString());
						
						String url = j3mManifest.getString(Manifest.Keys.URL);
						long pkc12Id = j3mManifest.getLong(Manifest.Keys.CERTS);
						
						Map<String, Object> postData = new HashMap<String, Object>();
						postData.put(Transport.Keys.GET_MESSAGES, j3mManifest.getString(Manifest.Keys.J3MBASE));
						postData.put(Manifest.Keys.FINGERPRINT, j3mManifest.getString(Manifest.Keys.FINGERPRINT));
						
						JSONObject res = (JSONObject) new JSONTokener(HttpUtility.executeHttpsPost(this, url, postData, Transport.MimeTypes.TEXT, pkc12Id)).nextValue();
						if(res.getString(Transport.Keys.RESULT).equals(Transport.Result.OK))
							parseMessages(res.getJSONArray(Transport.Keys.MESSAGES));
					}
					
				} catch(JSONException e) {
					Log.e(App.LOG, e.toString());
					e.printStackTrace();
					media.moveToNext();
					continue;
				}
				
				media.moveToNext();
			}
			media.close();
		}
	}
	
	@Override
	public void onClick(View v) {
		if(v == navigation)
			finish();
		
	}

	@Override
	public void onRouted() {
		h = new Handler();
		h.post(new Runnable() {
			@Override
			public void run() {
				getMessages();
			}
		});
		
	}

}
