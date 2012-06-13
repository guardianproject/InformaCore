package org.witness.informa;

import java.io.File;

import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.LoginCache;
import org.witness.mods.InformaButton;
import org.witness.ssc.MediaManager;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.R;

import com.actionbarsherlock.app.SherlockActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class ReviewAndFinish extends Activity implements OnClickListener {
	InformaButton confirmView, confirmShare, confirmTakeAnother;
	Uri shareImageUri, viewImageUri;
	SharedPreferences _sp;
	boolean canShare = true;
	long[] shareBase;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reviewandfinish);
		viewImageUri = Uri.parse(getIntent().getStringExtra(Keys.Media.Manager.VIEW_IMAGE_URI));
		if(getIntent().hasExtra(Keys.Media.Manager.SHARE_BASE)) {
			canShare = false;
			shareBase = getIntent().getLongArrayExtra(Keys.Media.Manager.SHARE_BASE);
		} else {
			shareImageUri = Uri.parse(getIntent().getStringExtra(Keys.Media.Manager.SHARE_IMAGE_URI));
		}
		
		_sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		confirmView = (InformaButton) findViewById(R.id.informaConfirm_btn_view);
		confirmView.setOnClickListener(this);
		
		confirmShare = (InformaButton) findViewById(R.id.informaConfirm_btn_share);
		if(canShare)
			confirmShare.setText(R.string.informaConfirm_btn_share);
		else
			confirmShare.setText(R.string.informaConfirm_btn_goToManager);
		confirmShare.setOnClickListener(this);
		
		
		confirmTakeAnother = (InformaButton) findViewById(R.id.informaConfirm_btn_takeAnother);
		confirmTakeAnother.setOnClickListener(this);
		
    	if(Integer.parseInt(_sp.getString(Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, "")) == LoginCache.AFTER_SAVE)
    		_sp.edit().putString(Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
	}
	
    private void viewImage() {
    	
    	Intent iView = new Intent(Intent.ACTION_VIEW);
    	iView.setType(ObscuraConstants.MIME_TYPE_JPEG);
    	iView.putExtra(Intent.EXTRA_STREAM, viewImageUri);
    	iView.setDataAndType(viewImageUri, ObscuraConstants.MIME_TYPE_JPEG);

    	startActivity(Intent.createChooser(iView, "View Image"));
    	finish();
	
    }
    
    private void viewVideo() {
    	Intent intent = new Intent(Intent.ACTION_VIEW);
    	intent.setDataAndType(viewImageUri, ObscuraConstants.MIME_TYPE_MP4);    	
   	 	startActivity(intent);
   	 	finish();
    }
    
    private void shareMedia() {
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	switch(getIntent().getIntExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO)) {
		case InformaConstants.MediaTypes.PHOTO:
			intent.setType(ObscuraConstants.MIME_TYPE_JPEG);
			break;
		case InformaConstants.MediaTypes.VIDEO:
			intent.setType(ObscuraConstants.MIME_TYPE_MP4);
			break;
    	}
    	intent.putExtra(Intent.EXTRA_STREAM, shareImageUri);
    	startActivity(Intent.createChooser(intent, getResources().getString(R.string.informaConfirm_share_prompt))); 
    }
    
    private void launchManager() {
    	Intent intent = new Intent(this, MediaManager.class);
    	intent.putExtra(Keys.Media.Manager.SHARE_BASE, shareBase);
    	startActivity(intent);
    	finish();
    }

	@Override
	public void onClick(View v) {
		if(v == confirmView) {	
			switch(getIntent().getIntExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO)) {
			case InformaConstants.MediaTypes.PHOTO:
				viewImage();
				break;
			case InformaConstants.MediaTypes.VIDEO:
				viewVideo();
				break;
			}
		} else if(v == confirmShare) {
			if(canShare)
				shareMedia();
			else
				launchManager();
		} else if(v == confirmTakeAnother) {
			setResult(SherlockActivity.RESULT_OK);
			finish();
		}
	}
}
