package org.witness.informacam.app;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.MediaManagerAdapter;
import org.witness.informacam.app.adapters.MediaManagerAdapter.OnMediaFocusedListener;
import org.witness.informacam.app.mods.InformaChoosableAlert;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.MediaManager;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.MediaManagerUtility.MediaManagerDisplay;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

public class MediaManagerActivity extends Activity implements OnClickListener, OnRoutedListener, OnMediaFocusedListener, OnChoosableChosenListener {
	
	Handler h;
	
	ImageButton navigation;
	ListView media_manager_list;
	ArrayList<MediaManagerDisplay> media;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initLayout();
		
		MainRouter.show(this);
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
		if(v == navigation)
			finish();
		
	}

	@Override
	public void onRouted() {
		getMedia();
	}

	@Override
	public void onMediaFocusedListener(int which) {
		InformaChoosableAlert alert = new InformaChoosableAlert(MediaManagerActivity.this, getResources().getStringArray(R.array.media_manager_actions), media.get(which));
		alert.setTitle("Media Manager");
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
			}
			break;
		case MediaManager.Actions.RENAME_MEDIA:
			break;
		case MediaManager.Actions.DELETE_MEDIA:
			break;
		}
		
	}

}
