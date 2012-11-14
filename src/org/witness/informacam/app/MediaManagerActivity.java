package org.witness.informacam.app;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.MediaManagerAdapter;
import org.witness.informacam.app.adapters.MediaManagerAdapter.OnMediaFocusedListener;
import org.witness.informacam.app.editors.image.ImageConstructor.ImageConstructorListener;
import org.witness.informacam.app.editors.video.VideoConstructor.VideoConstructorListener;
import org.witness.informacam.app.mods.InformaChoosableAlert;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.app.mods.InformaEditTextAlert;
import org.witness.informacam.app.mods.InformaEditTextAlert.AlertInputListener;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.MediaManager;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.MediaManagerUtility.MediaManagerDisplay;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

public class MediaManagerActivity extends Activity implements OnClickListener, OnRoutedListener, OnMediaFocusedListener, OnChoosableChosenListener, ImageConstructorListener, VideoConstructorListener, AlertInputListener {
	
	Handler h = new Handler();
	
	ImageButton navigation;
	ListView media_manager_list;
	ArrayList<MediaManagerDisplay> media;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initLayout();
		MainRouter.show(this);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	private void initLayout() {
		setContentView(R.layout.mediamanageractivity);
		
		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);
		
		media_manager_list = (ListView) findViewById(R.id.media_manager_list);
	}
	
	private void getMedia() {
		media = new ArrayList<MediaManagerDisplay>();
		for(JSONObject m : IOCipherService.getInstance().getSavedMedia()) {
			media.add(new MediaManagerDisplay(m));
		}
		media_manager_list.setAdapter(new MediaManagerAdapter(MediaManagerActivity.this, media));
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainactivity_menu, menu);
        menu.removeItem(R.id.menu_refresh);
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem mi) {
    	switch(mi.getItemId()) {
    	case R.id.extras_about:
    		MainRouter.launchAbout(MediaManagerActivity.this);
    		return true;
    	case R.id.extras_preferences:
    		MainRouter.launchPreferences(MediaManagerActivity.this);
    		return true;
    	case R.id.extras_knowledgebase:
    		MainRouter.launchKnowledgebase(MediaManagerActivity.this);
    		return true;
    	case R.id.extras_send_log:
    		MainRouter.launchSendLog(MediaManagerActivity.this);
    		return true;
    	case R.id.extras_logout:
    		MainRouter.doLogout(MediaManagerActivity.this);
    		finish();
    		return true;
    	case R.id.menu_export_public_key:
    		MainRouter.exportDeviceKey(MediaManagerActivity.this);
    		return true;
    	default:
    		return false;
    	}
    }
	
	@Override
	public void onClick(View v) {
		if(v == navigation) {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void onRouted() {
		h.post(new Runnable() {
			@Override
			public void run() {
				getMedia();
			}
		});
	}

	@Override
	public void onItemLongClick(int which) {
		InformaChoosableAlert alert = new InformaChoosableAlert(MediaManagerActivity.this, getResources().getStringArray(R.array.media_manager_actions), media.get(which));
		alert.setTitle(getString(R.string.media_manager));
		alert.show();
	}
	
	@Override
	public void onItemClick(Object obj) {
		try {
			getIntent().putExtra(Manifest.Keys.LOCATION_OF_ORIGINAL, ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL));
			setResult(Activity.RESULT_OK, getIntent());
			finish();
		} catch(JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
	}

	@Override
	public void onChoice(int which, Object obj) {
		switch(which) {
		case MediaManager.Actions.RENAME_MEDIA:
			try {
				InformaEditTextAlert ieta = new InformaEditTextAlert(MediaManagerActivity.this, obj);
			
				ieta.setTitle(getString(R.string.media_manager_rename));
				ieta.setMessage(getString(R.string.media_manager_rename_prompt) + " " + ((MediaManagerDisplay) obj).getString(Manifest.Keys.ALIAS));
				ieta.show();
			} catch(JSONException e) {
				Log.e(App.LOG, e.toString());
			}
			break;
		case MediaManager.Actions.EXPORT_MEDIA:
			/* TODO:
			 * 1) prompt to choose contact to make copy for ?
			 * 
			 * DONE:
			 * 2) make a version
			 * 3) save to public IO
			 * 4) export intent
			 */
			try {
				//String baseName = ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL).split("/")[1];
				String baseName = ((MediaManagerDisplay) obj).baseId;
				
				File originalMedia = IOCipherService.getInstance().getFile(((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL));
				
				java.io.File clone = null;
				if(((MediaManagerDisplay) obj).getInt(Media.Keys.TYPE) == Media.Type.IMAGE)
					clone = IOCipherService.getInstance().exportImageFromIOCipherToMemory(originalMedia, System.currentTimeMillis() + "_tmp_" + originalMedia.getName());
				else
					clone = IOCipherService.getInstance().moveFromIOCipherToMemory(Uri.fromFile(originalMedia), System.currentTimeMillis() + "_tmp_" + originalMedia.getName());
				
				File originalCache = IOCipherService.getInstance().getFile(baseName + "/cache.json");
				
				InformaService.getInstance().onInformaInitForExport(MediaManagerActivity.this, clone.getAbsolutePath(), originalCache, ((MediaManagerDisplay) obj).getInt(Manifest.Keys.MEDIA_TYPE));
				
			} catch(JSONException e) {}
			catch (IOException e) {
				Log.e(Storage.LOG, e.toString());
				e.printStackTrace();
			}
			break;
		case MediaManager.Actions.DELETE_MEDIA:
			try {
				String baseName = ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL).split("/")[1];
				IOCipherService.getInstance().delete(baseName);
				h.post(new Runnable() {
					@Override
					public void run() {
						getMedia();
					}
				});
				// TODO: get rid of working copy in DB
			} catch (JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
			break;
		}
	}
	
	@Override
	public void onSubmit(Object obj, String input) {
		try {
			// save the manifest again
			File manifestFile = IOCipherService.getInstance().getFile("/" + ((MediaManagerDisplay) obj).baseId + "/manifest.json");
			JSONObject manifest = (JSONObject) new JSONTokener(new String(IOUtility.getBytesFromFile(manifestFile))).nextValue();
			manifestFile.delete();
			
			manifest.put(Manifest.Keys.ALIAS, input);
			manifest.put(Manifest.Keys.LAST_SAVED, System.currentTimeMillis());
			
			byte[] manifestBytes = manifest.toString().getBytes();
			FileOutputStream fos = new FileOutputStream(manifestFile);
			fos.write(manifestBytes, 0, manifestBytes.length);
			fos.flush();
			fos.close();
			
			// update list view
			h.post(new Runnable() {
				@Override
				public void run() {
					getMedia();
				}
			});
		} catch(JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	private void doExport(java.io.File versionForExport) {
		Intent export = new Intent()
			.setAction(Intent.ACTION_SEND)
			.putExtra(Intent.EXTRA_TEXT, getString(R.string.media_manager_export_text))
			.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(versionForExport))
			.setType("*/*");
		startActivity(export);
	}

	@Override
	public void onExportVersionCreated(java.io.File versionForExport) {
		// returns the image for export intent
		doExport(versionForExport);
	}

	@Override
	public void onExportVersionCreated(java.io.File versionForExport, String clonePath) {
		// returns the video for export intent		
		// be sure to delete the file at the clone path!
		java.io.File clone = new java.io.File(clonePath);
		clone.delete();
		
		doExport(versionForExport);
	}

	@Override
	public void onCancel() {
		// TODO Auto-generated method stub
		
	}

}
