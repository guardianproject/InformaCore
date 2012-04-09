package org.witness.informa;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.secure.Apg;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class EncryptActivity extends Activity {
	SharedPreferences sp;
    ArrayList<Map<File, String>> images;
    Apg apg;
    
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		images = (ArrayList<Map<File, String>>) getIntent().getSerializableExtra(InformaConstants.Keys.ENCRYPTED_IMAGES);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		apg = Apg.getInstance();
		apg.setSignatureKeyId(sp.getLong(InformaConstants.Keys.Owner.SIG_KEY_ID, 0));
		if(apg.isAvailable(this)) {
			for(Map<File, String> image : images) {
				Entry<File, String> i = image.entrySet().iterator().next();
				apg.setEncryptionKeys(apg.getSecretKeyIdsFromEmail(this, i.getValue()));
				apg.encryptFile(this, i.getKey());
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		apg.onActivityResult(EncryptActivity.this, requestCode, resultCode, data);
		Log.d(InformaConstants.TAG, "fuck this apg");
		Log.d(InformaConstants.TAG, data.toString());
		
		
	}
	
	/*
	private void reviewAndFinish() {
    	Intent i = new Intent(this, ReviewAndFinish.class);
    	i.setData(savedImageUri);
    	i.putExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO);
    	startActivity(i);
    	finish();
    }
    */
}
