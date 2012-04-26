package org.witness.informa;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

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
		
		// email, metadatablob
		
		Log.d(InformaConstants.TAG, metadataToEncrypt.toString());
		progress = (InformaTextView) findViewById(R.id.encrypting_progress);
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if(encrypted > metadataToEncrypt.size()) {
			Entry<Long, String> e = ((Map<Long, String>) metadataToEncrypt.get(encrypted)).entrySet().iterator().next();
			progress.setText(getResources().getString(R.string.apg_encrypting_progress) + intendedDestination + "\u2026");
		
			apg.setEncryptionKeys(new long[] {e.getKey()});
			apg.encryptFile(this, new File(e.getValue()));
		} else {
			setResult(Activity.RESULT_OK);
			finish();
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == Activity.RESULT_OK) {
			
			apg.onActivityResult(this, requestCode, resultCode, data);
			switch(requestCode) {
			case Apg.ENCRYPT_MESSAGE:
				encrypted++;
				break;
			}
			
		}
	}
}
