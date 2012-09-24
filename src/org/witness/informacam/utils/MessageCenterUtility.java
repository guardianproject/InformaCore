package org.witness.informacam.utils;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.j3m.J3M.J3MManifest;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

public class MessageCenterUtility {
	public static class MessageCenterDisplay {
		public String alias, baseName, thumbnail, from;
		public long lastCheckedForMessages, certId;
		public int newMessages;
		
		public MessageCenterDisplay(Context c, J3MManifest j3mManifest, int newMessages) {
			try {
				baseName = j3mManifest.getString(Manifest.Keys.J3MBASE);
				alias = j3mManifest.getString(Manifest.Keys.J3MBASE);
				certId = j3mManifest.getLong(Manifest.Keys.CERTS);
				this.newMessages = newMessages;
				
				if(j3mManifest.has(Manifest.Keys.ALIAS))
					alias = j3mManifest.getString(Manifest.Keys.ALIAS);
				if(j3mManifest.has(Manifest.Keys.THUMBNAIL))
					thumbnail = j3mManifest.getString(Manifest.Keys.THUMBNAIL);
				
				if(j3mManifest.has(Manifest.Keys.LAST_MESSAGES))
					lastCheckedForMessages = j3mManifest.getLong(Manifest.Keys.LAST_MESSAGES);
				else
					lastCheckedForMessages = System.currentTimeMillis();
				
				if(j3mManifest.has(Manifest.Keys.TRUSTED_DESTINATION_DISPLAY_NAME))
					from = j3mManifest.getString(Manifest.Keys.TRUSTED_DESTINATION_DISPLAY_NAME);
				else
					from = c.getString(R.string.message_center_unknown);
				
				
			} catch(JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
		}
	}
	
	public static class MessageThreadDisplay {
		String content, from;
		long time;
		
		public MessageThreadDisplay(String uri, String trustedDestinationURL) {
			// look up the name of the td for "from"
			
			// get the content from the uri for "content"
			
			// parse the filename for the time
		}
	}
}
