package org.witness.ssc;

import java.io.File;

import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.SensorSucker;
import org.witness.informa.utils.SensorSucker.LocalBinder;
import org.witness.ssc.Eula.OnEulaAgreedTo;
import org.witness.ssc.InformaSettings.OnSettingsSeen;
import org.witness.ssc.image.ImageEditor;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

public class InformaApp extends SherlockActivity implements OnEulaAgreedTo, OnSettingsSeen {
	ActionBar ab;	
	private Uri uriCameraImage = null;
	
	SharedPreferences _sp;
	SharedPreferences.Editor _ed;
	boolean showHints = false;
	
	SensorSucker informaService;
	
	private ServiceConnection sc = new ServiceConnection() {
    	public void onServiceConnected(ComponentName cn, IBinder binder) {
    		LocalBinder lb = (LocalBinder) binder;
    		informaService = lb.getService();
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
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
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
        
        _sp = PreferenceManager.getDefaultSharedPreferences(this);
    	_ed = _sp.edit();
        
        if(getIntent().getExtras() != null) {
        	Bundle b = getIntent().getExtras();
        	if(b.containsKey(Keys.Service.FINISH_ACTIVITY)) {
        		this.sendBroadcast(new Intent().setAction(Keys.Service.STOP_SERVICE));
        		try {
        			unbindService(sc);
        		} catch(IllegalArgumentException e) {}
        		
        		if(Integer.parseInt(_sp.getString(Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, "")) == InformaConstants.LoginCache.ON_CLOSE)
        			doLogout();
        		
        		super.finish();
        	} else if(b.containsKey(Keys.Service.START_SERVICE)) {
        		Eula.show(this);
        	}
        		
        	
        } else {
        	Eula.show(this);
        }

    }
    
    @Override
	protected void onResume() {

		super.onResume();
		
		final SharedPreferences eula = getSharedPreferences(Eula.PREFERENCES_EULA,
                SherlockActivity.MODE_PRIVATE);
	  
        if (eula.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
        	boolean res = InformaSettings.show(this);
    		if(res)
    			launchInforma();
        }
				
	
	}

	private void setLayout() {
        setContentView(R.layout.mainmenu);
        ab = getSupportActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);
    }

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK)
		{
			setContentView(R.layout.mainloading);
			Intent passingIntent = new Intent(this, ImageEditor.class);
			passingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			
			if(requestCode == ObscuraConstants.GALLERY_RESULT)
				uriCameraImage = intent.getData();
			else if(requestCode == ObscuraConstants.CAMERA_RESULT)
				passingIntent.putExtra(InformaConstants.Keys.CaptureEvent.MEDIA_CAPTURE_COMPLETE, System.currentTimeMillis());
			
			if(uriCameraImage != null) {
				passingIntent.setData(uriCameraImage);
				startActivity(passingIntent);
			} else
				sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
		} else {
			sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
		}
		setLayout();
		
	}	

	private void displayAbout() {
		Intent intent = new Intent(this, About.class);
		startActivity(intent);
	}
	
	private void launchPrefs() {
		Intent intent = new Intent(this, Preferences.class);
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
        	case R.id.menu_logout:
        		doLogout();
        	case R.id.TakePictureButton:
        		setContentView(R.layout.mainloading);
    			
    	        if(storageState.equals(Environment.MEDIA_MOUNTED)) {
    	            ContentValues values = new ContentValues();
    	            values.put(MediaStore.Images.Media.TITLE, ObscuraConstants.CAMERA_TMP_FILE);
    	            values.put(MediaStore.Images.Media.DESCRIPTION,"ssctmp");
    	            
    	            File tmpFileDirectory = new File(ObscuraConstants.TMP_FILE_DIRECTORY);
    	            if (!tmpFileDirectory.exists())
    	            	tmpFileDirectory.mkdirs();
    	            
    	            File tmpFile = new File(tmpFileDirectory,"cam" + ObscuraConstants.TMP_FILE_NAME_IMAGE);
    	        	
    	        	uriCameraImage = Uri.fromFile(tmpFile);
    	        	sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.LOCK_LOGS));
    	            
    	        	Intent  intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    	        		.putExtra( MediaStore.EXTRA_OUTPUT, uriCameraImage);
    	            startActivityForResult(intent, ObscuraConstants.CAMERA_RESULT);
    	        }   else {
    	            new AlertDialog.Builder(InformaApp.this)
    	            .setMessage("External Storeage (SD Card) is required.\n\nCurrent state: " + storageState)
    	            .setCancelable(true).create().show();
    	        }
        		return true;
        	case R.id.ChooseGalleryButton:
        		setContentView(R.layout.mainloading);
        		
        		try {
        			Intent intent = new Intent(Intent.ACTION_PICK);
        			intent.setType("image/*");
        			startActivityForResult(intent, ObscuraConstants.GALLERY_RESULT);
        		} catch(Exception e) {
        			Toast.makeText(this, getString(R.string.gallery_launch_error), Toast.LENGTH_LONG).show();
        			Log.e(ObscuraConstants.TAG, "error loading gallery app? " + e.toString());
        		}
        		
        		return true;
        	default:
        		return false;
        }
    }
    
    private void launchInforma() {
    	// create folder if it doesn't exist
    	File informaDump = new File(InformaConstants.DUMP_FOLDER);
    	if(!informaDump.exists())
    		informaDump.mkdirs();
    	    
    	if(informaService == null) {
    		Intent startSensorSucker = new Intent(this, SensorSucker.class);
    		bindService(startSensorSucker, sc, Context.BIND_AUTO_CREATE);
    	}

    	showHints = _sp.getBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, true);

    	if(showHints) {
    		// TODO: show a popup
    	}
    	
    }
    
    public void doLogout() {
    	_ed.putString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
    	_sp = null;
    	_ed = null;
    	informaService = null;
    	super.finish();
    }
    
	
    /*
     * Handling screen configuration changes ourselves, 
     * we don't want the activity to restart on rotation
     */
    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
        // Reset the layout to use the landscape config
        setLayout();
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
    
}
