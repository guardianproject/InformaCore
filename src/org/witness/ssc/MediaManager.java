package org.witness.ssc;

import java.util.ArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.ssc.utils.MediaManagerAdapter;
import org.witness.ssc.utils.Selections;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

public class MediaManager extends Activity implements OnItemLongClickListener {
	SharedPreferences sp;
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	ListView mediaList;
	ArrayList<Selections> media;
	boolean isTopLevel = true;
	
	private static final String TAG = InformaConstants.TAG;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.media_manager);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		mediaList = (ListView) findViewById(R.id.mediaList);
		
		media = new ArrayList<Selections>();
		if(getIntent().hasExtra(Keys.Media.Manager.SHARE_BASE)) {
			try {
				getList(getIntent().getLongArrayExtra(Keys.Media.Manager.SHARE_BASE));
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
			isTopLevel = false;
		} else
			try {
				getList();
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
	}
	
	@Override
	public void onStop() {
		super.onStop();
		db.close();
		dh.close();
		sp = null;
	}
	
	private void getList(long[] shareBase) throws JSONException {
		dh.setTable(db, Tables.IMAGES);
		Object[] sb = new Object[shareBase.length];
		int o = 0;
		for(long l : shareBase) {
			sb[o] = l;
			o++;
		}
		Cursor c = dh.getMultiple(db, new String[] {
				Keys.Image.LOCATION_OF_OBSCURED_VERSION,
				Keys.Intent.Destination.EMAIL,
				Keys.Media.MEDIA_TYPE,
				Keys.Media.SHARE_VECTOR}, BaseColumns._ID, sb);
		if(c != null && c.getCount() > 0) {
			populateList(c);
		}
	}
	
	private void getList() throws JSONException {
		dh.setTable(db, Tables.IMAGES);
		Cursor c = dh.getValue(db, new String[] {
				Keys.Image.LOCATION_OF_OBSCURED_VERSION,
				Keys.Intent.Destination.EMAIL,
				Keys.Media.MEDIA_TYPE,
				Keys.Media.SHARE_VECTOR}, BaseColumns._ID, null);
		if(c != null && c.getCount() > 0) {
			populateList(c);
		}
	}
	
	private void populateList(Cursor c) throws JSONException {
		c.moveToFirst();
		while(!c.isAfterLast()) {
			JSONObject details = new JSONObject();
			details.put(Keys.Image.LOCATION_OF_OBSCURED_VERSION, c.getString(c.getColumnIndex(Keys.Image.LOCATION_OF_OBSCURED_VERSION)));
			details.put(Keys.Intent.Destination.EMAIL, c.getString(c.getColumnIndex(Keys.Intent.Destination.EMAIL)));
			details.put(Keys.Media.MEDIA_TYPE, c.getInt(c.getColumnIndex(Keys.Media.MEDIA_TYPE)));
			details.put(Keys.Media.SHARE_VECTOR, c.getInt(c.getColumnIndex(Keys.Media.SHARE_VECTOR)));
			
			String[] displayName = details.getString(Keys.Image.LOCATION_OF_OBSCURED_VERSION).split("/");
			media.add(new Selections(displayName[displayName.length - 1],false,details));
			
			c.moveToNext();
		}
		
		c.close();
		
		mediaList.setAdapter(new MediaManagerAdapter(this, media));
		mediaList.setOnItemLongClickListener(this);
	}
	
	@Override
	public void onBackPressed() {
		if(isTopLevel)
			finish();
		else {
			isTopLevel = true;
			try {
				getList();
			} catch (JSONException e) {
				Log.d(TAG, e.toString());
			}
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> av, View v, int which, long id) {
		
		return false;
	}

}
