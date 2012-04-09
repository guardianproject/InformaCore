package org.witness.ssc;

import org.witness.mods.InformaButton;
import org.witness.mods.InformaHeaderTextView;
import org.witness.mods.InformaTextView;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class About extends Activity implements OnClickListener {
	InformaHeaderTextView packageVersion;
	InformaButton dismiss;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		
		String versNum = "";
        
        try {
            String pkg = getPackageName();
            versNum = getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
        	versNum = "";
        }
        
        packageVersion = (InformaHeaderTextView) findViewById(R.id.about_packageVersion);
        packageVersion.setText(versNum);
        
        dismiss = (InformaButton) findViewById(R.id.about_dismiss);
        dismiss.setOnClickListener(this);
        
        
	}
	
	@Override
	public void onClick(View v) {
		if(v == dismiss)
			finish();
	}

}
