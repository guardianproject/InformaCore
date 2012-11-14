package org.witness.informacam.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.Eula.OnEulaAgreedTo;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.editors.image.ImageEditor;
import org.witness.informacam.app.editors.video.VideoEditor;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.crypto.SignatureService;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.InformaService.LocalBinder;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Exif;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.InformaMediaScanner;
import org.witness.informacam.utils.Time;
import org.witness.informacam.utils.InformaMediaScanner.OnMediaScannedListener;

import com.xtralogic.android.logcollector.SendLogActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.MenuInflater;
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
    
    List<BroadcastReceiver> br = new ArrayList<BroadcastReceiver>();
    
    private int GPS_WAITING = 0;
    
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
        
        br.add(new Broadcaster(new IntentFilter(App.Main.SERVICE_STARTED)));
        br.add(new Broadcaster(new IntentFilter(Transport.Errors.CONNECTION)));
		
        captureIntent = editorIntent = null;
        h = new Handler();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	for(BroadcastReceiver b : br)
			registerReceiver(b, ((Broadcaster) b).intentFilter);
    	
    	final SharedPreferences eula = getSharedPreferences(Eula.PREFERENCES_EULA, Activity.MODE_PRIVATE);
    	if(!eula.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
    		boolean eulaChoice = Eula.show(this);
    		if(eulaChoice)
    			onEulaAgreedTo();
    	} else
    		onEulaAgreedTo();
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	for(BroadcastReceiver b : br)
    		unregisterReceiver(b);
    }
    
	private void initInformaCam() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Intent launchDatabaseService = new Intent(MainActivity.this, DatabaseService.class);
		    	startService(launchDatabaseService);
			}
		}).start();
    	
    	h.postDelayed(new Runnable() {
			@Override
			public void run() {
				new Thread(new Runnable() {
					@Override
					public void run() {
						Intent launchInformaService = new Intent(MainActivity.this, InformaService.class);
						bindService(launchInformaService, sc, Context.BIND_AUTO_CREATE);
						
						Intent initVirtualStorage = new Intent(MainActivity.this, IOCipherService.class);
						startService(initVirtualStorage);
						
						Intent launchUploaderService = new Intent(MainActivity.this, UploaderService.class);
						startService(launchUploaderService);
						
						Intent launchSignatureUtility = new Intent(MainActivity.this, SignatureService.class);
						startService(launchSignatureUtility);
					}
				}).start();
			}
    		
    	}, 500);
    }
	
	private void refreshUploads() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				UploaderService.getInstance().restart();
			}
		}).start();
	}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if(informaService != null)
    		doShutdown();
    	
    	checkForLogout();
    }
    
    @Override
    public void onEulaAgreedTo() {
    	MainRouter.show(this);
    }
    
    private void checkForLogout() {
    	int loginPref = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.Keys.LOGIN_CACHE_TIME, null));
    	if(loginPref == Settings.LoginCache.ON_CLOSE)
    		MainRouter.doLogout(MainActivity.this);
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
    
    private void launchMediaManager() {
    	mProgressDialog = ProgressDialog.show(this, "", "please wait...", false, false);
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
    	h.postDelayed(new Runnable() {
    		@Override
    		public void run() {
    			if(informaService.getCurrentTime() <= 0) {
    				Log.d(App.LOG, "GPS NOT READY YET...");
    				GPS_WAITING++;
    				
    				if(GPS_WAITING > App.Main.GPS_WAIT_MAX) {
    					h.post(new Runnable() {
    						@Override
    						public void run() {
    							try {
    								mProgressDialog.cancel();
    							} catch(NullPointerException e) {}
    							ErrorHandler.show(MainActivity.this, getString(R.string.error_gps_nonresponsive));
    						}
    					});
    					
    				} else {
    					
    					h.postDelayed(this, 200);
    				}
    				
    				return;
    			}
    			
    			editorIntent.setData(mediaCaptureUri);
    			startActivityForResult(editorIntent, App.Main.FROM_EDITOR);
    		}
    	}, 200);
    }
    
    private void launchMediaCapture(final String tempFile) {
    	mProgressDialog = ProgressDialog.show(this, "", getString(R.string.please_wait), false, false);
    	
    	
    	if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
    		Toast.makeText(this, getResources().getString(R.string.error_media_mounted), Toast.LENGTH_LONG).show();
    		return;
    	}
    	
    	h.post(new Runnable() {
    		@Override
    		public void run() {
    			informaService.init();
    		}
    	});
    	
    	h.postDelayed(new Runnable() {
    		@Override
    		public void run() {
    			if(informaService.getCurrentTime() <= 0) {
    				Log.d(App.LOG, "GPS NOT READY YET...");
    				GPS_WAITING++;
    				
    				if(GPS_WAITING > App.Main.GPS_WAIT_MAX) {
    					h.post(new Runnable() {
    						@Override
    						public void run() {
    							try {
    								mProgressDialog.cancel();
    							} catch(NullPointerException e) {}
    							ErrorHandler.show(MainActivity.this, getString(R.string.error_gps_nonresponsive));
    						}
    					});
    					
    				} else {
    					
    					h.postDelayed(this, 200);
    				}
    				
    				return;
    			}
    			
    			try {
    	    		LogPack logPack = new LogPack(Informa.CaptureEvent.Keys.TYPE, Informa.CaptureEvent.TIMESTAMPS_RESOLVED);
    				logPack.put(Constants.Time.Keys.RELATIVE_TIME, System.currentTimeMillis());
    				informaService.onUpdate(logPack);
    				
    				ContentValues values = new ContentValues();
    		    	values.put(MediaStore.Images.Media.TITLE, tempFile);
    		    	values.put(MediaStore.Images.Media.DESCRIPTION, tempFile);
    		    	
    		    	mediaCaptureFile = new File(Storage.FileIO.DUMP_FOLDER, tempFile);
    		    	
    		    	if(tempFile.equals(Storage.FileIO.IMAGE_TMP)) {
    		    		mediaCaptureUri = Uri.fromFile(mediaCaptureFile);
    		    		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mediaCaptureUri);
    		    	}
    		    	
    		    	
    		    	startActivityForResult(captureIntent, App.Main.FROM_MEDIA_CAPTURE);
    			} catch (JSONException e) {
    				Log.e(App.LOG, e.toString());
    				e.printStackTrace();
    			}
    		}
    	}, 200);
    	
    	
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	try {
    		outState.putString(App.Main.MEDIA_CAPTURE_URI_SAVED_STATE, mediaCaptureUri.toString());
    		outState.putString(App.Main.MEDIA_CAPTURE_FILE_SAVED_STATE, mediaCaptureFile.getAbsolutePath());
    	} catch(NullPointerException e) {}
    	super.onSaveInstanceState(outState);
    }
    
    @Override
    public void onRestoreInstanceState(Bundle inState) {
    	Log.d(App.LOG, "RESTORE INSTANCE STATE CALLED BTW");
    	
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(resultCode == Activity.RESULT_OK) {
    		
    		switch(requestCode) {
    		
    		case App.Main.FROM_MEDIA_CAPTURE:    			
    			if(mediaCaptureFile.getName().equals(Storage.FileIO.IMAGE_TMP)) {
    				editorIntent = new Intent(this, ImageEditor.class);
    				mimeType = Media.Type.MIME_TYPE_JPEG;
    				mediaCaptureUri = Uri.fromFile(mediaCaptureFile);
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
    			h.post(new Runnable() {
					@Override
					public void run() {
						new InformaMediaScanner(MainActivity.this, mediaCaptureFile);
					}
				});
    			break;
    		case App.Main.FROM_EDITOR:
    			if(mProgressDialog != null)
    				mProgressDialog.dismiss();
    			
    			int loginPref = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.Keys.LOGIN_CACHE_TIME, null));
    			Log.d(App.LOG, "login pref= " + loginPref);
    	    	if(loginPref == Settings.LoginCache.AFTER_SAVE)
    	    		MainRouter.doLogout(MainActivity.this);
    			
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
    					h.post(new Runnable() {
    						@Override
    						public void run() {
    							new InformaMediaScanner(MainActivity.this, mediaCaptureFile);
    						}
    					});
    					
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
    	
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainactivity_menu, menu);
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem mi) {
    	switch(mi.getItemId()) {
    	case R.id.extras_about:
    		MainRouter.launchAbout(MainActivity.this);
    		return true;
    	case R.id.extras_preferences:
    		MainRouter.launchPreferences(MainActivity.this);
    		return true;
    	case R.id.extras_knowledgebase:
    		MainRouter.launchKnowledgebase(MainActivity.this);
    		return true;
    	case R.id.extras_send_log:
    		MainRouter.launchSendLog(MainActivity.this);
    		return true;
    	case R.id.extras_logout:
    		MainRouter.doLogout(MainActivity.this);
    		finish();
    		return true;
    	case R.id.menu_refresh:
    		refreshUploads();
    		return true;
    	case R.id.menu_export_public_key:
    		MainRouter.exportDeviceKey(MainActivity.this);
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
	}
	
	private void doShutdown() {
		unbindService(sc);
	}
	
	private class Broadcaster extends BroadcastReceiver {
		IntentFilter intentFilter;
		
		public Broadcaster(IntentFilter intentFilter) {
			this.intentFilter = intentFilter;
		}
		
		@Override
		public void onReceive(Context c, Intent i) {
			if(App.Main.SERVICE_STARTED.equals(i.getAction())) {
			} else if(Transport.Errors.CONNECTION.equals(i.getAction())) {
				Toast.makeText(MainActivity.this, getString(R.string.error_orbot_nonresponsive), Toast.LENGTH_LONG).show();
			}
			
		}
	}
}