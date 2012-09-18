package org.witness.informacam.app;

import info.guardianproject.iocipher.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.MediaManagerAdapter;
import org.witness.informacam.app.adapters.MediaManagerAdapter.OnMediaFocusedListener;
import org.witness.informacam.app.editors.image.ImageConstructor.ImageConstructorListener;
import org.witness.informacam.app.editors.video.VideoConstructor.VideoConstructorListener;
import org.witness.informacam.app.mods.InformaChoosableAlert;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

public class MediaManagerActivity extends Activity implements OnClickListener, OnRoutedListener, OnMediaFocusedListener, OnChoosableChosenListener, ImageConstructorListener, VideoConstructorListener {
	
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
			Log.d(App.LOG, m.toString());
			media.add(new MediaManagerDisplay(m));
		}
		media_manager_list.setAdapter(new MediaManagerAdapter(MediaManagerActivity.this, media));
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
	public void onMediaFocusedListener(int which) {
		InformaChoosableAlert alert = new InformaChoosableAlert(MediaManagerActivity.this, getResources().getStringArray(R.array.media_manager_actions), media.get(which));
		alert.setTitle(getString(R.string.media_manager));
		alert.show();
	}

	@Override
	public void onChoice(int which, Object obj) {
		switch(which) {
		case MediaManager.Actions.OPEN_MEDIA:
			try {
				getIntent().putExtra(Manifest.Keys.LOCATION_OF_ORIGINAL, ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL));
				setResult(Activity.RESULT_OK, getIntent());
				finish();
			} catch(JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
			break;
		case MediaManager.Actions.RENAME_MEDIA:
			break;
		case MediaManager.Actions.EXPORT_MEDIA:
			/* TODO:
			 * 1) prompt to choose contact to make copy for ?
			 * 2) make a version
			 * 3) save to public IO
			 * 4) export intent
			 */
			try {
				String baseName = ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL).split("/")[1];
				
				File originalMedia = IOCipherService.getInstance().getFile(((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL));
				java.io.File clone = IOCipherService.getInstance().moveFromIOCipherToMemory(Uri.fromFile(originalMedia), System.currentTimeMillis() + "_tmp_" + originalMedia.getName());
				
				File originalCache = IOCipherService.getInstance().getFile(baseName + "/cache.json");
				
				InformaService.getInstance().onInformaInitForExport(MediaManagerActivity.this, clone.getAbsolutePath(), originalCache, ((MediaManagerDisplay) obj).getInt(Manifest.Keys.MEDIA_TYPE));
				
			} catch(JSONException e) {}
			catch (IOException e) {
				Log.d(Storage.LOG, e.toString());
				e.printStackTrace();
			}
			break;
		case MediaManager.Actions.DELETE_MEDIA:
			try {
				String baseName = ((MediaManagerDisplay) obj).getString(Manifest.Keys.LOCATION_OF_ORIGINAL).split("/")[1];
				Log.d(App.LOG, "deleting " + baseName);
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
		Log.d(App.LOG, "heyo we have a clone: " + versionForExport.getAbsolutePath());
		doExport(versionForExport);
	}

	@Override
	public void onExportVersionCreated(java.io.File versionForExport, String clonePath) {
		// returns the video for export intent
		Log.d(App.LOG, "heyo we have a clone: " + versionForExport.getAbsolutePath());
		
		// be sure to delete the file at the clone path!
		java.io.File clone = new java.io.File(clonePath);
		clone.delete();
		
		doExport(versionForExport);
	}

}
