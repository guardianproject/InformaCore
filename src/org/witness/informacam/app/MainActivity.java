package org.witness.informacam.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.R;
import org.witness.informacam.app.Eula.OnEulaAgreedTo;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.editors.image.ImageEditor;
import org.witness.informacam.app.editors.video.VideoEditor;
import org.witness.informacam.crypto.SignatureUtility;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.InformaService.LocalBinder;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.j3m.J3M.J3MManifest;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Exif;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.InformaMediaScanner;
import org.witness.informacam.utils.Time;
import org.witness.informacam.utils.InformaMediaScanner.OnMediaScannedListener;

import com.xtralogic.android.logcollector.SendLogActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements OnEulaAgreedTo, OnClickListener, OnMediaScannedListener, OnRoutedListener {

    SharedPreferences sp;
    
    Button camera, camcorder, media_manager, message_center, address_book;
    
    private Uri mediaCaptureUri = null;
    private File mediaCaptureFile = null;
    private String mimeType = Media.Type.MIME_TYPE_JPEG;
    
    Intent captureIntent, editorIntent;
    
    Handler h;
    ProgressDialog mProgressDialog;
    
    InformaService informaService = null;
    boolean mustInitMetadata;
    
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        SQLiteDatabase.loadLibs(this);
        initLayout();
        
        captureIntent = editorIntent = null;
        h = new Handler();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	final SharedPreferences eula = getSharedPreferences(Eula.PREFERENCES_EULA, Activity.MODE_PRIVATE);
    	if(!eula.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
    		boolean eulaChoice = Eula.show(this);
    		if(eulaChoice)
    			onEulaAgreedTo();
    	} else
    		onEulaAgreedTo();
    }
    
    @SuppressWarnings("unused")
	private void initInformaCam() {
    	Intent launchDatabaseService = new Intent(this, DatabaseService.class);
    	startService(launchDatabaseService);
    	h.postDelayed(new Runnable() {

			@Override
			public void run() {
				SignatureUtility signatureUtility = new SignatureUtility(MainActivity.this);
				
				Intent launchInformaService = new Intent(MainActivity.this, InformaService.class);
				bindService(launchInformaService, sc, Context.BIND_AUTO_CREATE);
				
				Intent initVirtualStorage = new Intent(MainActivity.this, IOCipherService.class);
				startService(initVirtualStorage);
				
				Intent launchUploaderService = new Intent(MainActivity.this, UploaderService.class);
				startService(launchUploaderService);
				
			}
    		
    	}, 500);
    	
    	
    	
    	
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if(informaService != null)
    		doShutdown();
    }
    
    @Override
    public void onEulaAgreedTo() {
    	MainRouter.show(this);
    }
    
    private void initLayout() {
    	setContentView(R.layout.mainactivity);
    	
    	camera = (Button) findViewById(R.id.camera_button);
    	camera.setOnClickListener(this);
    	
    	camcorder = (Button) findViewById(R.id.camcorder_button);
    	camcorder.setOnClickListener(this);
    	
    	media_manager = (Button) findViewById(R.id.media_manager_button);
    	media_manager.setOnClickListener(this);
    	
    	message_center = (Button) findViewById(R.id.message_center_button);
    	message_center.setOnClickListener(this);
    	
    	address_book = (Button) findViewById(R.id.address_book_button);
    	address_book.setOnClickListener(this);
    }
    
    private void launchPreferences() {
    	Intent intent = new Intent(this, PreferencesActivity.class);
    	startActivity(intent);
    }
    
    private void launchSendLog() {
    	Intent intent = new Intent(this, SendLogActivity.class);
    	startActivity(intent);
    }
    
    private void launchAbout() {
    	Intent intent = new Intent(this, AboutActivity.class);
    	startActivity(intent);
    }
    
    private void launchKnowledgebase() {
    	
    }
    
    private void launchMediaManager() {
    	Intent intent = new Intent(this, MediaManagerActivity.class);
    	startActivityForResult(intent, App.Main.FROM_MEDIA_MANAGER);
    }
    
    private void launchMessageCenter() {
    	Intent intent = new Intent(this, MessageCenterActivity.class);
    	startActivity(intent);
    }
    
    private void launchAddressBook() {
    	Intent intent = new Intent(this, AddressBookActivity.class);
    	startActivity(intent);
    }
    
    private void launchEditor() throws IOException {
    	editorIntent.setData(mediaCaptureUri);
    	startActivityForResult(editorIntent, App.Main.FROM_EDITOR);
    }
    
    private void launchMediaCapture(String tempFile) {
    	mProgressDialog = ProgressDialog.show(this, "", "please wait...", false, false);
    	
    	if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
    		Toast.makeText(this, getResources().getString(R.string.error_media_mounted), Toast.LENGTH_LONG).show();
    		return;
    	}
    	
    	informaService.init();
    	
    	ContentValues values = new ContentValues();
    	values.put(MediaStore.Images.Media.TITLE, tempFile);
    	values.put(MediaStore.Images.Media.DESCRIPTION, tempFile);
    	
    	mediaCaptureFile = new File(Storage.FileIO.DUMP_FOLDER, tempFile);
    	
    	if(tempFile.equals(Storage.FileIO.IMAGE_TMP)) {
    		mediaCaptureUri = Uri.fromFile(mediaCaptureFile);
    		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mediaCaptureUri);
    	}
    	
    	startActivityForResult(captureIntent, App.Main.FROM_MEDIA_CAPTURE);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(resultCode == Activity.RESULT_OK) {
    		switch(requestCode) {
    		
    		case App.Main.FROM_MEDIA_CAPTURE:    			
    			Log.d(App.LOG, mediaCaptureFile.getAbsolutePath());
    			if(mediaCaptureFile.getName().equals(Storage.FileIO.IMAGE_TMP)) {
    				editorIntent = new Intent(this, ImageEditor.class);
    			} else {
    				editorIntent = new Intent(this, VideoEditor.class);
    				mimeType = Media.Type.MIME_TYPE_MP4;
    				mediaCaptureUri = data.getData();
    				FileOutputStream fos;
					try {
						fos = new FileOutputStream(mediaCaptureFile);
						InputStream media = getContentResolver().openInputStream(mediaCaptureUri);
						byte buf[] = new byte[1024];
						int len;
						while((len = media.read(buf)) > 0)
							fos.write(buf, 0, len);
						fos.close();
						media.close();
					} catch (FileNotFoundException e) {
						Log.e(App.LOG, e.toString());
					} catch (IOException e) {
						Log.e(App.LOG, e.toString());
					}
    			}
    			
    			mustInitMetadata = true;
    			new InformaMediaScanner((MainActivity) this, mediaCaptureFile);
    			break;
    		case App.Main.FROM_EDITOR:
    			// TODO: add to upload queue, restart informa...
    			if(mProgressDialog != null)
    				mProgressDialog.dismiss();
    			break;
    		case App.Main.FROM_MEDIA_MANAGER:
    			// copy original to what should be mediaCaptureFile and set mediaCaptureUri
    			String filePointer = data.getStringExtra(Manifest.Keys.LOCATION_OF_ORIGINAL);
    			String tempFile = null;
    			
    			if(filePointer.contains(Media.Type.JPEG)) {
    				tempFile = Storage.FileIO.IMAGE_TMP;
    				editorIntent = new Intent(this, ImageEditor.class);
    			} else {
    				tempFile = Storage.FileIO.VIDEO_TMP;
    				editorIntent = new Intent(this, VideoEditor.class);
    			}
    			
    			mediaCaptureFile = IOCipherService.getInstance().cipherFileToJavaFile(data.getStringExtra(Manifest.Keys.LOCATION_OF_ORIGINAL), tempFile);
    			mediaCaptureUri = Uri.fromFile(mediaCaptureFile);

    			// init logs
    			informaService.init();
    			informaService.inflateMediaCache(filePointer.split("original.")[0] + "cache.json");
    			
    			// launch editor
    			try {
    				if(tempFile.equals(Storage.FileIO.IMAGE_TMP))
    					launchEditor();
    				else {
    					// Guess what?  You have to run this through the media scanner if you want
    					// it to load in the video editor activity!  doesn't that suck?!  (yes.)
    					mustInitMetadata = false;
    					new InformaMediaScanner((MainActivity) this, mediaCaptureFile);
    				}
				} catch (IOException e) {
					Log.e(App.LOG, e.toString());
					e.printStackTrace();
				}
    			break;
    		}
    	} else if(resultCode == Activity.RESULT_CANCELED) {
    		if(mProgressDialog != null)
    			mProgressDialog.dismiss();
    		
    		if(informaService != null) {
    			try {
    				informaService.suspend();
    			} catch(NullPointerException e) {}
    		}
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	return super.onCreateOptionsMenu(menu);
    	// TODO: init menu pls
    }
    
    public boolean onOptionsItemSelected(MenuItem mi) {
    	switch(mi.getItemId()) {
    	case R.id.extras_about:
    		launchAbout();
    		return true;
    	case R.id.extras_preferences:
    		launchPreferences();
    		return true;
    	case R.id.extras_knowledgebase:
    		launchKnowledgebase();
    		return true;
    	case R.id.extras_send_log:
    		launchSendLog();
    		return true;
    	case R.id.extras_logout:
    		return true;
    	default:
    		return false;
    	}
    }

	@Override
	public void onClick(View v) {
		if(v == camera) {
			captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			launchMediaCapture(Storage.FileIO.IMAGE_TMP);
		} else if(v == camcorder) {
			captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			launchMediaCapture(Storage.FileIO.VIDEO_TMP);
		} else if(v == media_manager) {
			launchMediaManager();
		} else if(v == message_center) {
			launchMessageCenter();
		} else if(v == address_book) {
			launchAddressBook();
		}
		
	}

	@Override
	public void onMediaScanned(Uri uri) {
		mediaCaptureUri = uri;
		h.post(new Runnable() {
			@Override
			public void run() {
				try {
					if(mustInitMetadata) {
						LogPack logPack = IOUtility.getMetadata(mediaCaptureUri, mediaCaptureFile.getAbsolutePath(), mimeType, MainActivity.this);
						informaService.onUpdate(Time.timestampToMillis(logPack.get(Exif.TIMESTAMP).toString()), logPack);
						Log.d(App.LOG, "finished scanning the media:\n" + logPack.toString());
					}
    				
    				launchEditor();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void onRouted() {
		initInformaCam();
		//doHttpTest();
	}
	
	private void doShutdown() {
		unbindService(sc);
	}
}