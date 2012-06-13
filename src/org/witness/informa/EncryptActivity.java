package org.witness.informa;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
import org.witness.informa.utils.InformaConstants.Keys.Service;
import org.witness.informa.utils.InformaConstants.Keys.Settings;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.VideoConstructor;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.io.Uploader;
import org.witness.informa.utils.io.Uploader.LocalBinder;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.DestoService;
import org.witness.informa.utils.secure.MediaHasher;
import org.witness.mods.InformaTextView;
import org.witness.ssc.R;
import org.witness.ssc.video.ShellUtils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
	SharedPreferences sp;
	int encrypted = 0;
	Apg apg;
	
	Uploader uploader, _uploader;
	private List<BroadcastReceiver> br;
	Handler h;
	
	private ServiceConnection sc = new ServiceConnection() {
    	public void onServiceConnected(ComponentName cn, IBinder binder) {
    		LocalBinder lb = (LocalBinder) binder;
    		uploader = lb.getService();
    	}
    	
    	public void onServiceDisconnected(ComponentName cn) {
    		uploader = null;
    		_uploader = null;
    	}
    };
	
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
		
		br = new ArrayList<BroadcastReceiver>();
		br.add(new Broadcaster(new IntentFilter(Service.UPLOADER_AVAILABLE)));
		
		metadataPacks = new ArrayList<MetadataPack>();
		
		progress = (InformaTextView) findViewById(R.id.encrypting_progress);
		
		Intent startUploader = new Intent(this, Uploader.class);
		bindService(startUploader, sc, Context.BIND_AUTO_CREATE);
	}
	
	public String getKeyHash() {
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
	
	private void init() {
		dh.setTable(db, Tables.IMAGES);
		final ArrayList<String> destos = new ArrayList<String>();
		for(Map<Long, String> mo : metadataToEncrypt) {
			Entry<Long, String> e = mo.entrySet().iterator().next();
			Cursor img = dh.getValue(db, new String[] {Image.METADATA, Image.UNREDACTED_IMAGE_HASH, Image.TRUSTED_DESTINATION, Media.MEDIA_TYPE}, BaseColumns._ID, (Long) e.getKey());
			if(img != null && img.getCount() == 1) {
				img.moveToFirst();
				for(String s : img.getColumnNames())
					Log.d(InformaConstants.TAG, s + " : " + img.getString(img.getColumnIndex(s)));
				Log.d(InformaConstants.TAG, img.toString());
				metadataPacks.add(new MetadataPack(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)), img.getString(img.getColumnIndex(Image.METADATA)), e.getValue(), img.getString(img.getColumnIndex(Image.UNREDACTED_IMAGE_HASH)), img.getInt(img.getColumnIndex(Media.MEDIA_TYPE)), getKeyHash()));
				destos.add(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)));
				img.close();
			}
		}
		
		progress.setText(getString(R.string.encrypt_contacting_td));
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				destoService = new DestoService(dh, db);
				try {
					for(Map<String, String> desto : destoService.getDestos(destos)) {
						for(MetadataPack mp : metadataPacks) {
							if(desto.containsKey(mp.email))
								mp.setTDDestination(desto.get(mp.email));
						}
					}
					
					for(final MetadataPack mp : metadataPacks) {
						if(sp.getBoolean(Keys.Settings.WITH_ENCRYPTION, false))
							mp.doEncrypt();
						mp.doInject();
					}
					
					_uploader.addToQueue(metadataPacks);
					
				} catch (JSONException e) {
					Log.e(InformaConstants.TAG, "Error getting destos: " + e.toString());
				} catch (IOException e) {
					Log.e(InformaConstants.TAG, "Error getting destos: " + e.toString());
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
	
	@Override
	public void onStop() {
		super.onStop();
		db.close();
		dh.close();
	}
	
	private class Broadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;
		
		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if(Service.UPLOADER_AVAILABLE.equals(intent.getAction())) {
				_uploader = Uploader.getUploader();
				init();
			}
		}
	}
	
	public class MetadataPack {
		public String email, metadata, filepath, clonepath, keyHash;
		public String tdDestination = null;
		public String tmpId, authToken, hash;
		public int mediaType;
		public long timestampCreated;
		
		public MetadataPack(
				String email, String metadata, String filepath, 
				String hash, int mediaType, String keyHash) {
			this.email = email;
			this.metadata = metadata;
			this.filepath = filepath;
			this.clonepath = EncryptActivity.this.clonePath;
			this.hash = hash;
			this.mediaType = mediaType;
			this.keyHash = keyHash;
		}
		
		public void setTDDestination(String tdDestination) {
			this.tdDestination = tdDestination;
		}
		
		public void doEncrypt() {
			// TODO: once we have GPG/PGP working...
			// until then, just sign data with the key
			
		}
		
		public void doInject() throws IOException, JSONException {
			if(mediaType == MediaTypes.PHOTO)
				timestampCreated = ImageConstructor.constructImage(this);
			else if(mediaType == MediaTypes.VIDEO)
				timestampCreated = VideoConstructor.constructVideo(EncryptActivity.this, this, new ShellUtils.ShellCallback() {
					
					@Override
					public void shellOut(char[] msg) {
						
						
					}
				});
		}
	}	
}
