package org.witness.ssc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.client.ClientProtocolException;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Device;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.LoginCache;
import org.witness.informa.utils.InformaConstants.Keys.Service;
import org.witness.informa.utils.SensorSucker;
import org.witness.informa.utils.SensorSucker.Broadcaster;
import org.witness.informa.utils.SensorSucker.LocalBinder;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.io.InformaMediaScanner;
import org.witness.informa.utils.io.Uploader;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.SignatureUtil;
import org.witness.ssc.Eula.OnEulaAgreedTo;
import org.witness.ssc.InformaSettings.OnSettingsSeen;
import org.witness.ssc.image.ImageEditor;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.utils.ObscuraConstants.MediaScanner;
import org.witness.ssc.video.VideoEditor;
import org.witness.ssc.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.xtralogic.android.logcollector.SendLogActivity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

public class InformaApp extends SherlockActivity implements OnEulaAgreedTo, OnSettingsSeen {
	ActionBar ab;	
	private Uri uriCameraImage = null;
	ArrayList<Broadcaster> br;
	
	SharedPreferences _sp;
	SharedPreferences.Editor _ed;
	boolean showHints = false;
	
	SensorSucker informaService;
	Uploader uploader;
	Intent passingIntent;
	File cameraImage;
	
	public SignatureUtil signatureUtil = null;
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	private ServiceConnection sc = new ServiceConnection() {
    	public void onServiceConnected(ComponentName cn, IBinder binder) {
    		LocalBinder lb = (LocalBinder) binder;
    		informaService = lb.getService();
    		Log.d(InformaConstants.SUCKER_TAG, "SERVICE ABSOLUTELY STARTED");
    	}
    	
    	public void onServiceDisconnected(ComponentName cn) {
    		informaService = null;
    	}
    };
		
	@Override
	protected void onDestroy() 
	{
		super.onDestroy();
		deleteTmpFile();
		sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.STOP_SERVICE));
		signatureUtil = null;
		
		try {
			if(Integer.parseInt(_sp.getString(Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, "")) == LoginCache.ON_CLOSE)
				doLogout();
		} catch(NullPointerException e) {} // the user has already logged out, and prefs are null
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		for(BroadcastReceiver b : br)
			unregisterReceiver(b);
	}
	
	private void deleteTmpFile ()
	{
		File fileDir = getExternalFilesDir(null);		
		if (fileDir == null || !fileDir.exists())
			fileDir = getFilesDir();
		
		File tmpFile = new File(fileDir,ObscuraConstants.CAMERA_TMP_FILE);
		if (tmpFile.exists())
			tmpFile.delete();
	}
				
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	setTheme(R.style.Theme_Sherlock_Light);
        super.onCreate(savedInstanceState);

        setLayout();
        deleteTmpFile();
                
        informaService = null;
        
        SQLiteDatabase.loadLibs(this);
        
        _sp = PreferenceManager.getDefaultSharedPreferences(this);
    	_ed = _sp.edit();
    	
    	File tmpFileDirectory = new File(InformaConstants.DUMP_FOLDER);
        if (!tmpFileDirectory.exists())
        	tmpFileDirectory.mkdirs();
        
        br = new ArrayList<Broadcaster>();
        br.add(new Broadcaster(new IntentFilter(MediaScanner.SCANNED))); 
        br.add(new Broadcaster(new IntentFilter(Service.UPLOADER_AVAILABLE)));
    }
    
    @Override
	protected void onResume() {
		super.onResume();
		for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b).intentFilter);
		
		final SharedPreferences eula = getSharedPreferences(Eula.PREFERENCES_EULA,
                SherlockActivity.MODE_PRIVATE);
        if (!eula.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
        	boolean res = Eula.show(this);
    		if(res)
    			launchInforma();
        } else
        	onEulaAgreedTo();
		
	}

	private void setLayout() {
        setContentView(R.layout.mainmenu);
        ab = getSupportActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);
    }

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK && requestCode != ObscuraConstants.IMAGE_EDITOR) {
			setContentView(R.layout.mainloading);
			
			passingIntent = null;
			try {
				uriCameraImage = intent.getData();
			} catch(NullPointerException e) {
				// The getData param is null.
				// probably because it is a camera thing.
				Log.e(InformaConstants.TAG, "the getData param is null.  uriCameraImage is unchanged");
			}
			Log.d(InformaConstants.TAG, "RETURNED URI FROM CAMERA RESULT: " + uriCameraImage.toString());
			
			String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uriCameraImage.toString());
			String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
			
			try {
				if(mimeType.compareTo(ObscuraConstants.MIME_TYPE_MP4) == 0)
					passingIntent = new Intent(this, VideoEditor.class);
				else if(mimeType.compareTo(ObscuraConstants.MIME_TYPE_JPEG) == 0)
					passingIntent = new Intent(this, ImageEditor.class);
			} catch(NullPointerException e) {
				if(uriCameraImage.getPathSegments().contains("video")) {
					passingIntent = new Intent(this, VideoEditor.class);
					mimeType = ObscuraConstants.MIME_TYPE_MP4;
				} else if(mimeType == null && uriCameraImage.getPathSegments().contains("images")) {
					passingIntent = new Intent(this, ImageEditor.class);
					mimeType = ObscuraConstants.MIME_TYPE_JPEG;
				}
			}
			
			if(requestCode == ObscuraConstants.CAMERA_RESULT) {
				
				passingIntent
					.putExtra(InformaConstants.Keys.CaptureEvent.MEDIA_CAPTURE_COMPLETE, System.currentTimeMillis())
					.putExtra(InformaConstants.Keys.Genealogy.MEDIA_ORIGIN, InformaConstants.Genealogy.MediaOrigin.FROM_INFORMA);
				// TODO: IMPORTANTE!  Right here, we are forcing the media object to go through
				// the media scanner.  THIS MUST BE UNDONE at the end of the editing process
				// in order to maintain security/anonymity
				
				if(mimeType.equals(ObscuraConstants.MIME_TYPE_MP4)) {
					// write input stream to file
					FileOutputStream fos;
					try {
						fos = new FileOutputStream(cameraImage);
						InputStream media = getContentResolver().openInputStream(uriCameraImage);
						byte buf[] = new byte[1024];
						int len;
						while((len = media.read(buf)) > 0)
							fos.write(buf, 0, len);
						fos.close();
						media.close();
					} catch (FileNotFoundException e) {
						Log.e(InformaConstants.TAG, e.toString());
					} catch (IOException e) {
						Log.e(InformaConstants.TAG, e.toString());
					}
					
				}
				
				new InformaMediaScanner(this, cameraImage);
				return;
				
				
			} else
				passingIntent.putExtra(InformaConstants.Keys.Genealogy.MEDIA_ORIGIN, InformaConstants.Genealogy.MediaOrigin.IMPORT);
			
			launchEditor();
		} else {
			if(requestCode == ObscuraConstants.IMAGE_EDITOR) {
				launchInforma();
			}	
		}
		setLayout();
		
	}	
	
	private void launchEditor() {
		if(uriCameraImage != null && passingIntent != null) {
			passingIntent.setData(uriCameraImage);
			passingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);				
			startActivityForResult(passingIntent, ObscuraConstants.IMAGE_EDITOR);
		} else
			sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
	}

	private void displayAbout() {
		Intent intent = new Intent(this, About.class);
		startActivity(intent);
	}
	
	private void launchPrefs() {
		Intent intent = new Intent(this, Preferences.class);
		startActivity(intent);
	}
	
	private void sendLog() {
		Intent intent = new Intent(this, SendLogActivity.class);
		startActivity(intent);
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater mi = getSupportMenuInflater();
		mi.inflate(R.menu.main_menu, menu);
    	return super.onCreateOptionsMenu(menu);
	}
	
    public boolean onOptionsItemSelected(MenuItem item) {	
    	String storageState = Environment.getExternalStorageState();
        switch (item.getItemId()) {
        	case R.id.menu_about:
        		displayAbout();
        		return true;
        	case R.id.menu_prefs:
        		launchPrefs();
        		return true;
        	case R.id.menu_send_log:
        		sendLog();
        		return true;
        	case R.id.menu_logout:
        		doLogout();
        	case R.id.TakePictureButton:
        		setContentView(R.layout.mainloading);
    			
    	        if(storageState.equals(Environment.MEDIA_MOUNTED)) {
    	            ContentValues values = new ContentValues();
    	            values.put(MediaStore.Images.Media.TITLE, ObscuraConstants.CAMERA_TMP_FILE);
    	            values.put(MediaStore.Images.Media.DESCRIPTION,"ssctmp");
    	            
    	            cameraImage = new File(InformaConstants.DUMP_FOLDER, "cam" + ObscuraConstants.TMP_FILE_NAME_IMAGE);
    	        	sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.LOCK_LOGS));
    	        	
    	        	uriCameraImage = Uri.fromFile(cameraImage);
    	        	
    	        	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uriCameraImage);
    	            startActivityForResult(intent, ObscuraConstants.CAMERA_RESULT);
    	        } else {
    	        	new AlertDialog.Builder(InformaApp.this)
    	            	.setMessage("External Storeage (SD Card) is required.\n\nCurrent state: " + storageState)
    	            	.setCancelable(true).create().show();
    	        }
        		return true;
        	case R.id.TakeVideoButton:
    			//Toast.makeText(this, "Sorry, video is not yet available for this version", Toast.LENGTH_LONG).show();
    			setContentView(R.layout.mainloading);
    	        if(storageState.equals(Environment.MEDIA_MOUNTED)) {
    	            ContentValues values = new ContentValues();
    	            values.put(MediaStore.Images.Media.TITLE, ObscuraConstants.CAMCORDER_TMP_FILE);
    	            values.put(MediaStore.Images.Media.DESCRIPTION,"ssctmp");
    	            
    	            cameraImage = new File(InformaConstants.DUMP_FOLDER, "vid" + ObscuraConstants.TMP_FILE_NAME_VIDEO);
    	        	sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.LOCK_LOGS));
    	        	
    	        	Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    	            startActivityForResult(intent, ObscuraConstants.CAMERA_RESULT);
    	        } else {
    	        	new AlertDialog.Builder(InformaApp.this)
    	            	.setMessage("External Storeage (SD Card) is required.\n\nCurrent state: " + storageState)
    	            	.setCancelable(true).create().show();
    	        }

        		return true;
        	case R.id.ChooseGalleryButton:
        		setContentView(R.layout.mainloading);
        		
        		try {
        			Intent intent = new Intent(Intent.ACTION_PICK);
        			//intent.setType("image/*");
        			intent.setType("video/*");
        			startActivityForResult(intent, ObscuraConstants.GALLERY_RESULT);
        		} catch(Exception e) {
        			Toast.makeText(this, getString(R.string.gallery_launch_error), Toast.LENGTH_LONG).show();
        			Log.e(ObscuraConstants.TAG, "error loading gallery app? " + e.toString());
        		}
        		//Toast.makeText(this, "Gallery import is not available for this version of InformaCam", Toast.LENGTH_LONG).show();
        		return true;
        	case R.id.MediaManagerButton:
        		Intent intent = new Intent(this, MediaManager.class);
        		startActivity(intent);
        		return true;
        	default:
        		return false;
        }
    }
    
    private void launchSigUtil() {
    	if(signatureUtil == null) {
	    	dh = new DatabaseHelper(this);
	    	db = dh.getReadableDatabase(_sp.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
	    	dh.setTable(db, Tables.KEYRING);
	    	
	    	Cursor k = dh.getValue(db, new String[] {Device.PRIVATE_KEY, Device.PASSPHRASE}, BaseColumns._ID, Long.toString(1));
	    	if(k != null && k.getCount() == 1) {
	    		k.moveToFirst();
	    		signatureUtil = new SignatureUtil(k.getBlob(k.getColumnIndex(Device.PRIVATE_KEY)), k.getString(k.getColumnIndex(Device.PASSPHRASE)));
	    		k.close();
	    	}
	    	
	    	db.close();
	    	dh.close();
    	}
    }
    
    private void launchInforma() {
    	// create folder if it doesn't exist
    	File informaDump = new File(InformaConstants.DUMP_FOLDER);
    	if(!informaDump.exists())
    		informaDump.mkdirs();
    	
    	launchSigUtil();
    	
    	if(informaService == null) {
    		Intent startSensorSucker = new Intent(this, SensorSucker.class);
    		bindService(startSensorSucker, sc, Context.BIND_AUTO_CREATE);
    		
    	}

    	showHints = _sp.getBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, true);

    	if(showHints) {
    		// TODO: show a popup
    	}
    	
    	sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
    	
    	Intent startUploader = new Intent(this, Uploader.class);
		startService(startUploader);
    }
    
    public void doLogout() {
    	_ed.putString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
    	_sp = null;
    	_ed = null;
    	sendBroadcast(new Intent().setAction(Keys.Service.STOP_SERVICE));
		try {
			unbindService(sc);
			informaService = null;
		} catch(IllegalArgumentException e) {}
		moveTaskToBack(true);
    }

	@Override
	public void onEulaAgreedTo() {
		boolean res = InformaSettings.show(this);
		if(res) {
			launchInforma();
		}
	}
	
	@Override
	public void onSettingsSeen() {}
    
	private class Broadcaster extends BroadcastReceiver {
		public IntentFilter intentFilter;
		
		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}
		
		@Override
		public void onReceive(Context c, Intent i) {
			if(MediaScanner.SCANNED.equals(i.getAction())) {
				Log.d(InformaConstants.TAG, "scanned. go.");
				uriCameraImage = i.getParcelableExtra(MediaScanner.URI);
				launchEditor();
			} else if(Service.UPLOADER_AVAILABLE.equals(i.getAction())) {
				uploader = Uploader.getUploader();
			}
		}
	}
}
