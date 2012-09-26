package org.witness.informacam.app;

import java.util.HashMap;

import org.witness.informacam.R;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion.Stego;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class StegoHideActivity extends Activity implements OnClickListener {
	HashMap<String, String> mProps;
	EditText stegoMessageHolder;
	Button stegoSubmit;
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.stegohideactivity);
		
		mProps = (HashMap<String, String>) getIntent().getSerializableExtra(App.ImageEditor.Keys.PROPERTIES);
		stegoMessageHolder = (EditText) findViewById(R.id.stegoMessageHolder);
		if(mProps.get(Stego.MESSAGE).compareTo("") != 0)
			stegoMessageHolder.setText(mProps.get(Stego.MESSAGE));
		
		if(getIntent().hasExtra(ImageRegion.THUMBNAIL)) {
			byte[] ba = getIntent().getByteArrayExtra(ImageRegion.THUMBNAIL);
			ImageView imageRegionThumb = (ImageView) findViewById(R.id.imageRegionThumb);
			imageRegionThumb.setImageBitmap(IOUtility.getBitmapFromBytes(ba, false));
		}
		
		stegoSubmit = (Button) findViewById(R.id.stegoSubmit);
		stegoSubmit.setOnClickListener(this);
	}
	
	@Override
	public void onClick(View v) {
		if(v == stegoSubmit) {
			if(stegoMessageHolder.getText().toString().compareTo("") != 0) {
				mProps.put(Stego.MESSAGE, stegoMessageHolder.getText().toString());
				
				getIntent().putExtra(ImageRegion.TAGGER_RETURN, mProps);
				getIntent().putExtra(ImageRegion.INDEX, getIntent().getIntExtra(ImageRegion.INDEX, 0));
				setResult(Activity.RESULT_OK,getIntent());
				
				finish();
				
			} else
				Toast.makeText(this, getResources().getString(R.string.error_stego_blank), Toast.LENGTH_LONG).show();
		}
		
	}

}
