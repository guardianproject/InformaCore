package org.witness.informacam.app;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.R;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.MessageThreadAdapter;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class MessageThreadActivity extends Activity implements OnClickListener, OnRoutedListener {
	String threadBaseStr, fromUrl, fromName, userPgp;
	long certId;
	
	TextView navigationLabel;
	EditText messageReply;
	Button messageSubmit;
	ListView messageThreads;
	List<Map<Long, String>> messageContent;
	ImageButton navigation;
	
	Handler h;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			threadBaseStr = getIntent().getStringExtra(App.MessageCenter.Keys.THREAD_BASE);
			certId = getIntent().getLongExtra(App.MessageCenter.Keys.CERT_ID, 0L);
		} catch(NullPointerException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
			finish();
		}		
		
		initLayout();
		MainRouter.show(this);
	}
	
	private void initLayout() {
		setContentView(R.layout.messagethreadactivity);
		messageThreads = (ListView) findViewById(R.id.message_thread_list);
		
		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);
		
		navigationLabel = (TextView) findViewById(R.id.navigation_label);
		
		messageSubmit = (Button) findViewById(R.id.message_reply_send);
		messageSubmit.setOnClickListener(this);
		
		messageReply = (EditText) findViewById(R.id.message_reply_content);
	}
	
	private void refreshThreads() {
		messageContent = null;
		File threadBase = IOCipherService.getInstance().getFile(threadBaseStr + "/messages");
		List<File> messages = IOCipherService.getInstance().walk(threadBase);
		for(File m : messages) {
			if(!m.getName().equals(".") && !m.getName().equals("..")) {
				if(messageContent == null)
					messageContent = new ArrayList<Map<Long, String>>();
				
				Map<Long, String> msg = new HashMap<Long, String>();
				try {
					msg.put(Long.parseLong(m.getName().split("_")[0]), new String(IOUtility.getBytesFromFile(m)));
					messageContent.add(msg);
				} catch(NumberFormatException e) {
					Log.e(App.LOG, e.toString() + "\nskipping this message");
				}
			}
		}
		
		messageThreads.setAdapter(new MessageThreadAdapter(this, messageContent));
	}
	
	private void loadThreads() {
		h = new Handler();
		// get who from and displayname off of certId
		DatabaseHelper dh = DatabaseService.getInstance().getHelper();
		SQLiteDatabase db = DatabaseService.getInstance().getDb();
		dh.setTable(db, Tables.Keys.KEYRING);
		Cursor c = dh.getValue(db, new String[] {PGP.Keys.PGP_DISPLAY_NAME, TrustedDestination.Keys.URL}, Crypto.Keyring.Keys.ID, certId);
		if(c != null && c.moveToFirst()) {
			fromName = c.getString(c.getColumnIndex(PGP.Keys.PGP_DISPLAY_NAME));
			fromUrl = c.getString(c.getColumnIndex(TrustedDestination.Keys.URL));
			c.close();
		}
		navigationLabel.setText(getString(R.string.message_center_conversation_with) + " " + fromName);
		
		userPgp = KeyUtility.getMyFingerprint(dh, db);
		
		refreshThreads();
	}
	
	private void sendMessage() {
		try {
			// make file called time_hash_R.txt (where R means it's from you)
			String content = messageReply.getText().toString();
			File message = new File(threadBaseStr + "/messages/" + System.currentTimeMillis() + "_" + MediaHasher.hash(content.getBytes(), "MD5") + "_R.txt");
			
			FileOutputStream fos = new FileOutputStream(message);
			fos.write(content.getBytes());
			fos.flush();
			fos.close();
			// refresh queue
			
			h.post(new Runnable() {

				@Override
				public void run() {
					refreshThreads();
					messageReply.setText("");
				}
				
			});
			
			// upload
			Map<String, Object> postData = new HashMap<String, Object>();
			postData.put(Transport.Keys.CLIENT_PGP, userPgp);
			postData.put(Transport.Keys.PUT_MESSAGE, threadBaseStr);
			
			JSONObject res = ((JSONObject) new JSONTokener(HttpUtility.executeHttpsPost(getApplicationContext(), fromUrl, postData, Transport.MimeTypes.TEXT, certId, IOUtility.getBytesFromFile(message), message.getName(), Transport.MimeTypes.TEXT)).nextValue()).getJSONObject(Transport.Keys.RES);
			if(res.getString(Transport.Keys.RESULT).equals(Transport.Result.FAIL)) {
				// TODO: handle failed result?
			}
			Log.d(App.LOG, res.toString());
			
		} catch (NoSuchAlgorithmException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
		
		
		
	}

	@Override
	public void onClick(View v) {
		if(v == navigation) {
			// TODO: put result: canceled?
			finish();
		} else if(v == messageSubmit && messageReply.getText().toString().length() > 0 && messageReply.getText().toString() != " ") {
			new Thread(new Runnable() {
				@Override
				public void run() {
					sendMessage();
				}
			}).start();
		}
		
	}

	@Override
	public void onRouted() {
		loadThreads();
		
	}
	
	
}
