package org.witness.informacam.app;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.app.MessageThreadActivity;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.MessageCenterAdapter;
import org.witness.informacam.app.adapters.MessageCenterAdapter.MessageCenterAdapterListener;
import org.witness.informacam.j3m.J3M.J3MManifest;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.MessageCenterUtility.MessageCenterDisplay;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

public class MessageCenterActivity extends Activity implements OnClickListener, OnRoutedListener, MessageCenterAdapterListener {
	
	Handler h = new Handler();
	DatabaseHelper dh = DatabaseService.getInstance().getHelper();
	SQLiteDatabase db = DatabaseService.getInstance().getDb();
	
	ImageButton navigation;
	
	ListView messageCenterList;
	List<MessageCenterDisplay> mcd;
	
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
		
		messageCenterList = (ListView) findViewById(R.id.message_center_list);
		
	}
	
	private void updateList() {
		messageCenterList.setAdapter(new MessageCenterAdapter(MessageCenterActivity.this, mcd));
	}
	
	private void parseMessages(J3MManifest j3mManifest, JSONArray newMessages, int oldMessages) {
		
		for(int m=0; m<newMessages.length(); m++) {
			
			try {
				JSONObject msg = newMessages.getJSONObject(m);
				
				// make messages dir if doesnt exist
				File msgDir = IOCipherService.getInstance().getFile(j3mManifest.getString(Media.Manifest.Keys.J3MBASE) + "/messages");
				if(!msgDir.exists())
					msgDir.mkdir();
				
				// copy contents into file
				File msgFile = new File(msgDir, msg.getString(Transport.Keys.Message.URL));
				FileWriter fw = new FileWriter(msgFile);
				fw.write(msg.getString(Transport.Keys.Message.CONTENT));
				fw.flush();
				fw.close();
				
			} catch(JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
		}
	}
	
	private void getMessages() {
		if(mcd != null)
			mcd.clear();
		
		mcd = new ArrayList<MessageCenterDisplay>();
		
		dh.setTable(db, Tables.Keys.MEDIA);
		Cursor media = dh.getValue(db, new String[] {Media.Keys.J3M_MANIFEST}, null, null);
		if(media != null && media.moveToFirst()) {
			while(!media.isAfterLast()) {
				byte[] jmd = null;
				try {
					jmd = media.getBlob(media.getColumnIndex(Media.Keys.J3M_MANIFEST));
				} catch(Exception e) {
					media.moveToNext();
					continue;
				}
				
				try {
					final J3MManifest j3mManifest = new J3MManifest((JSONObject) new JSONTokener(new String(jmd)).nextValue());
					if(j3mManifest.has(Manifest.UPLOADED_FLAG) && j3mManifest.getBoolean(Manifest.UPLOADED_FLAG)) {						
						List<File> receivedMessages = IOCipherService.getInstance().walk(IOCipherService.getInstance().getFile(j3mManifest.getString(Manifest.Keys.J3MBASE) + "/messages"));
						final StringBuffer sb = new StringBuffer();
						int oldMessages = 0;
						for(File msg : receivedMessages) {
							if(!msg.getName().equals(".") && !msg.getName().equals("..")) {
								sb.append(",\"" + msg.getName() + "\"");
								oldMessages++;
							}
						}
						
						if(oldMessages > 0) {
							h.post(new Runnable() {
								@Override
								public void run() {
									// update list
									mcd.add(new MessageCenterDisplay(MessageCenterActivity.this, j3mManifest, 0));
									updateList();
								}
							});
						}
						
						final int _oldMessages = oldMessages;
						
						h.post(new Runnable() {
							@Override
							public void run() {
								try {
									String url = j3mManifest.getString(Manifest.Keys.URL);
									long pkc12Id = j3mManifest.getLong(Manifest.Keys.CERTS);
									
									Map<String, Object> postData = new HashMap<String, Object>();
									postData.put(Transport.Keys.GET_MESSAGES, j3mManifest.getString(Manifest.Keys.J3MBASE));
									postData.put(Manifest.Keys.FINGERPRINT, j3mManifest.getString(Manifest.Keys.FINGERPRINT));
									if(sb.length() > 0)
										postData.put(Transport.Keys.READ_ARRAY, "[" + sb.toString().substring(1) + "]");
									
									String query = HttpUtility.executeHttpsPost(MessageCenterActivity.this, url, postData, Transport.MimeTypes.TEXT, pkc12Id);
									
									JSONObject res = ((JSONObject) new JSONTokener(query).nextValue()).getJSONObject(Transport.Keys.RES);
									
									if(res.getString(Transport.Keys.RESULT).equals(Transport.Result.OK) && res.has(Transport.Keys.BUNDLE))
										parseMessages(j3mManifest, res.getJSONObject(Transport.Keys.BUNDLE).getJSONArray(Transport.Keys.MESSAGES), _oldMessages);
								} catch(JSONException e) {
									Log.e(App.LOG, e.toString());
									e.printStackTrace();
								}
							}
						});
						
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
		//getMessages();
	}

	@Override
	public void onMessageClicked(Object obj) {
		Intent intent = new Intent(this, MessageThreadActivity.class)
			.putExtra(App.MessageCenter.Keys.THREAD_BASE, ((MessageCenterDisplay) obj).baseName)
			.putExtra(App.MessageCenter.Keys.CERT_ID, ((MessageCenterDisplay) obj).certId);
		startActivityForResult(intent, App.MessageCenter.Actions.VIEW_THREAD);
		
		
	}

}
