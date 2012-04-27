package org.witness.informa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.witness.informa.utils.ImageConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.secure.Apg;
import org.witness.mods.InformaTextView;
import org.witness.ssc.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class EncryptActivity extends Activity {
	InformaTextView progress;
	String intendedDestination, metadataString;
	ArrayList<Map<Long, String>> metadataToEncrypt;
	List<Map<String, String>> encryptedMetadata;
	int encrypted = 0;
	Apg apg;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.encrypt_activity);
		
		apg = Apg.getInstance();
		if(!apg.isAvailable(this))
			finish();
		
		metadataToEncrypt = (ArrayList<Map<Long, String>>) getIntent().getSerializableExtra(Keys.Service.ENCRYPT_METADATA);
		encryptedMetadata = new ArrayList<Map<String, String>>();
		
		// email, metadatablob
		
		Log.d(InformaConstants.TAG, metadataToEncrypt.toString());
		progress = (InformaTextView) findViewById(R.id.encrypting_progress);
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if(encrypted < metadataToEncrypt.size()) {
			Iterator<Entry<Long, String>> i = ((Map<Long, String>) metadataToEncrypt.get(encrypted)).entrySet().iterator();
			
			while(i.hasNext()) {
				Entry<Long, String> e = i.next();
				if(e.getKey() == 0L)
					intendedDestination = e.getValue();
				else {
					apg.setEncryptionKeys(new long[] {e.getKey()});
					try {
						apg.encrypt(this, new String(ImageConstructor.fileToBytes(new File(e.getValue()))));
					} catch (IOException ioe) {
						Log.e(InformaConstants.TAG, ioe.toString());
					}
				}
			}
			
			progress.setText(getResources().getString(R.string.apg_encrypting_progress) + " " + intendedDestination + "\u2026");
		} else {
			setResult(Activity.RESULT_OK, new Intent().putExtra(Keys.Service.ENCRYPT_METADATA, metadataToEncrypt));
			finish();
		}
	}
	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == Activity.RESULT_OK) {
			Log.d(InformaConstants.TAG, "this data was passed:\n" + data.toString());
			
			apg.onActivityResult(this, requestCode, resultCode, data);
			switch(requestCode) {
			case Apg.ENCRYPT_MESSAGE:
				encrypted++;
				break;
			}	
		}
	}
}
