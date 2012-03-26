package org.witness.sscphase1;

import java.io.File;

import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.SensorSucker;
import org.witness.informa.utils.SensorSucker.LocalBinder;
import org.witness.securesmartcam.ImageEditor;
import org.witness.ssc.video.VideoEditor;
import org.witness.sscphase1.Eula.OnEulaAgreedTo;
import org.witness.sscphase1.InformaSettings.OnSettingsSeen;
import org.witness.securesmartcam.utils.ObscuraConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;

public class ObscuraApp extends Activity implements OnClickListener, OnEulaAgreedTo, OnSettingsSeen {
	private Button choosePictureButton, chooseVideoButton, takePictureButton, takeVideoButton;		
	
	private Uri uriCameraImage = null;
	
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
		// TODO: this is only for testing...
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
        super.onCreate(savedInstanceState);

        setLayout();
        deleteTmpFile();
                
        informaService = null;
        
        if(getIntent().getExtras() != null) {
        	Bundle b = getIntent().getExtras();
        	if(b.containsKey(Keys.Service.FINISH_ACTIVITY))
        		finish();
        	else if(b.containsKey(Keys.Service.START_SERVICE)) {
        		Log.d(InformaConstants.TAG, "um hi? restart?");
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
                Activity.MODE_PRIVATE);
	  
        if (eula.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
        	boolean res = InformaSettings.show(this);
    		if(res)
    			launchInforma();
        }
				
	
	}

	private void setLayout() {
        setContentView(R.layout.mainmenu);
        
    	choosePictureButton = (Button) this.findViewById(R.id.ChoosePictureButton);
    	choosePictureButton.setOnClickListener(this);
    	
    	chooseVideoButton = (Button) this.findViewById(R.id.ChooseVideoButton);
    	chooseVideoButton.setOnClickListener(this);
    	
    	takePictureButton = (Button) this.findViewById(R.id.TakePictureButton);
    	takePictureButton.setOnClickListener(this);
    	
    	takeVideoButton = (Button) this.findViewById(R.id.TakeVideoButton);
    	takeVideoButton.setOnClickListener(this);
    }

	public void onClick(View v) {
		if (v == choosePictureButton) 
		{
			
			try
			{
				setContentView(R.layout.mainloading);
				Intent intent = new Intent(Intent.ACTION_PICK);
				intent.setType("image/*"); //limit to image types for now
				startActivityForResult(intent, ObscuraConstants.GALLERY_RESULT);
				
			}
			catch (Exception e)
			{
				Toast.makeText(this, "Unable to open Gallery app", Toast.LENGTH_LONG).show();
				Log.e(ObscuraConstants.TAG, "error loading gallery app to choose photo: " + e.getMessage(), e);
			}
			
		}
		else if (v == chooseVideoButton) 
		{
			
			try
			{
				 setContentView(R.layout.mainloading);
				Intent intent = new Intent(Intent.ACTION_PICK);
				intent.setType("video/*"); //limit to image types for now
				startActivityForResult(intent, ObscuraConstants.GALLERY_RESULT);
				
			}
			catch (Exception e)
			{
				Toast.makeText(this, "Unable to open Gallery app", Toast.LENGTH_LONG).show();
				Log.e(ObscuraConstants.TAG, "error loading gallery app to choose photo: " + e.getMessage(), e);
			}
			
		}
		else if (v == takePictureButton || v == takeVideoButton) {
			
			setContentView(R.layout.mainloading);
			
			String storageState = Environment.getExternalStorageState();
	        if(storageState.equals(Environment.MEDIA_MOUNTED)) {

	          
	            ContentValues values = new ContentValues();
	            Intent  intent = new Intent();
	            String tmpFileName = ObscuraConstants.TMP_FILE_NAME_IMAGE;;
	            
	            if(v == takePictureButton) {
	            	intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE );
	            	values.put(MediaStore.Images.Media.TITLE, ObscuraConstants.CAMERA_TMP_FILE);
	            } else if(v == takeVideoButton) {
	            	intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
	            	values.put(MediaStore.Images.Media.TITLE, ObscuraConstants.CAMCORDER_TMP_FILE);
	            	tmpFileName = ObscuraConstants.TMP_FILE_NAME_VIDEO;
	            }
	      
	            values.put(MediaStore.Images.Media.DESCRIPTION,"ssctmp");

	            File tmpFileDirectory = new File(ObscuraConstants.TMP_FILE_DIRECTORY);
	            if (!tmpFileDirectory.exists())
	            	tmpFileDirectory.mkdirs();
	            
	            File tmpFile = new File(tmpFileDirectory,"cam" + tmpFileName);
	        	
	        	uriCameraImage = Uri.fromFile(tmpFile);
	            //uriCameraImage = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
	        	sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.LOCK_LOGS));
	            
	            intent.putExtra( MediaStore.EXTRA_OUTPUT, uriCameraImage);
	            startActivityForResult(intent, ObscuraConstants.CAMERA_RESULT);
	        }   else {
	            new AlertDialog.Builder(ObscuraApp.this)
	            .setMessage("External Storeage (SD Card) is required.\n\nCurrent state: " + storageState)
	            .setCancelable(true).create().show();
	        }
	        
			takePictureButton.setVisibility(View.VISIBLE);
			takeVideoButton.setVisibility(View.VISIBLE);
			choosePictureButton.setVisibility(View.VISIBLE);
			chooseVideoButton.setVisibility(View.VISIBLE);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		
		
		if (resultCode == RESULT_OK)
		{
			setContentView(R.layout.mainloading);
			
			if (requestCode == ObscuraConstants.GALLERY_RESULT) 
			{
				if (intent != null)
				{
					Uri uriGalleryFile = intent.getData();
						
					if (uriGalleryFile != null)
					{
						Cursor cursor = managedQuery(uriGalleryFile, null, 
                                null, null, null); 
						cursor.moveToNext(); 
						// Retrieve the path and the mime type 
						String path = cursor.getString(cursor 
						                .getColumnIndex(MediaStore.MediaColumns.DATA)); 
						String mimeType = cursor.getString(cursor 
						                .getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
						
						if (mimeType == null || mimeType.startsWith("image"))
						{
							Intent passingIntent = new Intent(this,ImageEditor.class);
							passingIntent.setData(uriGalleryFile);
							startActivityForResult(passingIntent, ObscuraConstants.IMAGE_EDITOR);
						}
						else if (mimeType.startsWith("video"))
						{

							Intent passingIntent = new Intent(this,VideoEditor.class);
							passingIntent.setData(uriGalleryFile);
							startActivityForResult(passingIntent, ObscuraConstants.VIDEO_EDITOR);
						}
					}
					else
					{
						Toast.makeText(this, "Unable to load photo.", Toast.LENGTH_LONG).show();
	
					}
				}
				else
				{
					Toast.makeText(this, "Unable to load photo.", Toast.LENGTH_LONG).show();
	
				}
					
			}
			else if (requestCode == ObscuraConstants.CAMERA_RESULT)
			{
				//Uri uriCameraImage = intent.getData();
				
				if (uriCameraImage != null)
				{
					Intent passingIntent = null;
					int rc = -1;
					
					if(MimeTypeMap.getFileExtensionFromUrl(uriCameraImage.getPath()).compareTo(ObscuraConstants.MimeTypes.JPEG) == 0) {
						passingIntent = new Intent(this,ImageEditor.class);
						rc = ObscuraConstants.IMAGE_EDITOR;
					}
						
					else if(MimeTypeMap.getFileExtensionFromUrl(uriCameraImage.getPath()).compareTo(ObscuraConstants.MimeTypes.MP4) == 0) {
						passingIntent = new Intent(this, VideoEditor.class);
						rc = ObscuraConstants.VIDEO_EDITOR;
					}
					
					if(passingIntent != null) {
						passingIntent.setData(uriCameraImage);
						passingIntent.putExtra(InformaConstants.Keys.CaptureEvent.MEDIA_CAPTURE_COMPLETE, System.currentTimeMillis());
						startActivityForResult(passingIntent, resultCode);
					} else {
						takePictureButton.setVisibility(View.VISIBLE);
						takeVideoButton.setVisibility(View.VISIBLE);
						choosePictureButton.setVisibility(View.VISIBLE);
						chooseVideoButton.setVisibility(View.VISIBLE);
						
						sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
					}
				}
				else
				{
					takePictureButton.setVisibility(View.VISIBLE);
					takeVideoButton.setVisibility(View.VISIBLE);
					choosePictureButton.setVisibility(View.VISIBLE);
					chooseVideoButton.setVisibility(View.VISIBLE);
					
					sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
				}
			}
		}
		else {
			sendBroadcast(new Intent().setAction(InformaConstants.Keys.Service.UNLOCK_LOGS));
			setLayout();
		}
		
		
		
	}	

	/*
	 * Display the about screen
	 */
	private void displayAbout() {
		
		StringBuffer msg = new StringBuffer();
		
		msg.append(getString(R.string.app_name));
		
        String versNum = "";
        
        try {
            String pkg = getPackageName();
            versNum = getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
        	versNum = "";
        }
        
        msg.append(" v" + versNum);
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about));
	        
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about2));
        
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about3));
        
		showDialog(msg.toString());
	}
	
	private void launchPrefs() {
		Intent intent = new Intent(this, Preferences.class);
		startActivity(intent);
	}
	
	private void showDialog (String msg)
	{
		 new AlertDialog.Builder(this)
         .setTitle(getString(R.string.app_name))
         .setMessage(msg)
         .create().show();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		
    	MenuItem aboutMenuItem = menu.add(Menu.NONE, ObscuraConstants.ABOUT, Menu.NONE, getString(R.string.menu_about));
    	aboutMenuItem.setIcon(R.drawable.ic_menu_about);
    	
    	MenuItem prefsMenuItem = menu.add(Menu.NONE, ObscuraConstants.PREFS, Menu.NONE, getString(R.string.menu_prefs));
    	prefsMenuItem.setIcon(R.drawable.ic_menu_prefs);
    	
    	MenuItem logoutMenuItem = menu.add(Menu.NONE, ObscuraConstants.LOGOUT, Menu.NONE, getString(R.string.menu_logout));
    	logoutMenuItem.setIcon(R.drawable.ic_menu_logout);
    	
    	return true;
	}
	
    public boolean onOptionsItemSelected(MenuItem item) {	
        switch (item.getItemId()) {
        	case ObscuraConstants.ABOUT:
        		displayAbout();
        		return true;
        	case ObscuraConstants.PREFS:
        		launchPrefs();
        		return true;
        	case ObscuraConstants.LOGOUT:
        		doLogout();
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
    	
    }
    
    public void doLogout() {
    	SharedPreferences _sp = PreferenceManager.getDefaultSharedPreferences(this);
    	SharedPreferences.Editor _ed = _sp.edit();
    	_ed.putString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
    	finish();
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
		if(res)
			launchInforma();
	}
	
	@Override
	public void onSettingsSeen() {

	}
    
}
