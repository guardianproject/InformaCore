package org.witness.informa;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.witness.informa.utils.ImageConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Image;
import org.witness.informa.utils.InformaConstants.Keys.Media;
import org.witness.informa.utils.InformaConstants.Keys.Settings;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Media.ShareVector;
import org.witness.informa.utils.InformaConstants.Media.Status;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.MetadataPack;
import org.witness.informa.utils.VideoConstructor;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.io.Uploader;
import org.witness.informa.utils.secure.DestoService;
import org.witness.informa.utils.secure.MediaHasher;
import org.witness.mods.InformaTextView;
import org.witness.ssc.R;
import org.witness.ssc.video.ShellUtils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class EncryptActivity extends Activity {
	InformaTextView progress;
	String clonePath;
	ArrayList<Map<Long, String>> metadataToEncrypt;
	ArrayList<MetadataPack> metadataPacks;
	ArrayList<String> unknownDestos;
	
	private DatabaseHelper dh;
	private SQLiteDatabase db;
	private DestoService destoService;
	private String keyHash;
	SharedPreferences sp;
	int encrypted = 0;
	
	Uploader uploader;
	private List<BroadcastReceiver> br;
	Handler h;
	
	@SuppressWarnings({ "unchecked"})
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.encrypt_activity);
		
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		clonePath = getIntent().getStringExtra(Keys.Service.CLONE_PATH);
		
		// long id of metadatablob, filename
		metadataToEncrypt = (ArrayList<Map<Long, String>>) getIntent().getSerializableExtra(Keys.Service.ENCRYPT_METADATA);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Settings.HAS_DB_PASSWORD, ""));
		keyHash = getKeyHash();
		
		br = new ArrayList<BroadcastReceiver>();
		
		metadataPacks = new ArrayList<MetadataPack>();
		uploader = Uploader.getUploader();
		
		progress = (InformaTextView) findViewById(R.id.encrypting_progress);
		init();
	}
	
	private String getKeyHash() {
		dh.setTable(db, Tables.KEYRING);
		String keyHash = null;
		Cursor kh = dh.getValue(db, new String[] {Keys.Device.PUBLIC_KEY}, BaseColumns._ID, 1);
		if(kh != null && kh.getCount() == 1) {
			kh.moveToFirst();
			try {
				keyHash = MediaHasher.hash(kh.getBlob(kh.getColumnIndex(Keys.Device.PUBLIC_KEY)), "SHA-1");
			} catch (NoSuchAlgorithmException e) {
				Log.e(InformaConstants.TAG, e.toString());
			} catch (IOException e) {
				Log.e(InformaConstants.TAG, e.toString());
			}
			kh.close();
		}
		return keyHash;
	}
	
	private void reviewAndFinish() {
		/* there are 3 cases
		 * 1: unencrypted image: intent to share immediately
		 * 2: encrypted to several parties, all of which are in upload queue
		 * 3: encrypted to several parties, not in upload queue but should be shared somehow?
		 * 
		 * solutions:
		 * 1: share intent just launches that intent
		 * 2 & 3: go to "view in media manager", where you can audit those images 
		 */
		Intent intent = new Intent(this, ReviewAndFinish.class);
		intent.putExtra(Keys.Media.Manager.VIEW_IMAGE_URI, metadataPacks.get(0).clonepath);
		if(metadataPacks.get(0).shareVector == ShareVector.UNENCRYPTED_NOT_UPLOADED) {
			//share however
			intent.putExtra(Keys.Media.Manager.SHARE_IMAGE_URI, metadataPacks.get(0).filepath);
		} else {
			long[] shareBase = new long[metadataPacks.size()];
			for(int s=0; s<metadataPacks.size(); s++)
				shareBase[s] = metadataPacks.get(s).id;
			
			intent.putExtra(Keys.Media.Manager.SHARE_BASE, shareBase);
		}
		startActivity(intent);
		finish();
	}
	
	private void init() {
		dh.setTable(db, Tables.IMAGES);
		final ArrayList<Map<String, Long>> destos = new ArrayList<Map<String, Long>>();
		for(Map<Long, String> mo : metadataToEncrypt) {
			Entry<Long, String> e = mo.entrySet().iterator().next();
			Cursor img = dh.getValue(db, new String[] {Image.METADATA, Image.UNREDACTED_IMAGE_HASH, Image.TRUSTED_DESTINATION, Media.MEDIA_TYPE}, BaseColumns._ID, (Long) e.getKey());
			if(img != null && img.getCount() == 1) {
				img.moveToFirst();
				metadataPacks.add(new MetadataPack(clonePath, (Long) e.getKey(), img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)), img.getString(img.getColumnIndex(Image.METADATA)), e.getValue(), img.getString(img.getColumnIndex(Image.UNREDACTED_IMAGE_HASH)), img.getInt(img.getColumnIndex(Media.MEDIA_TYPE)), keyHash));
				Map<String, Long> mediaRecord = new HashMap<String, Long>();
				mediaRecord.put(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)), (Long) e.getKey());
				destos.add(mediaRecord);
				img.close();
			}
		}
		
		progress.setText(getString(R.string.encrypt_contacting_td));
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				destoService = new DestoService(EncryptActivity.this);
				try {
					for(Map<String, String> desto : destoService.getDestos(destos)) {
						for(MetadataPack mp : metadataPacks) {
							if(desto.containsKey(mp.email)) {
								mp.setTDDestination(desto.get(mp.email));
								mp.setShareVector(ShareVector.UNENCRYPTED_UPLOAD_QUEUE);
							}
						}
					}
					
					for(final MetadataPack mp : metadataPacks) {
						if(sp.getBoolean(Keys.Settings.WITH_ENCRYPTION, false))
							mp.doEncrypt();
						mp.doInject();
					}
					
					uploader.addToQueue(metadataPacks);
					uploader.showNotification();
					reviewAndFinish();
					
				} catch (JSONException e) {
					Log.e(InformaConstants.TAG, "Error getting destos: " + e.toString());
				} catch (IOException e) {
					Log.e(InformaConstants.TAG, "Error getting destos: " + e.toString());
				} catch (KeyManagementException e) {
					Log.e(InformaConstants.TAG, "Error initing ssl connection: " + e.toString());
				} catch (NoSuchAlgorithmException e) {
					Log.e(InformaConstants.TAG, "Error initing ssl connection: " + e.toString());
				}
				
			}
			
		}).start();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b).intentFilter);
		
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		for(BroadcastReceiver b : br)
			unregisterReceiver(b);
	}
	
	private class Broadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;
		
		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			
		}
	}		
}
