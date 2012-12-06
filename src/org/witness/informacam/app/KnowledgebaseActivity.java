package org.witness.informacam.app;

import org.witness.informacam.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageButton;

public class KnowledgebaseActivity extends Activity implements OnClickListener {
	ImageButton navigation;
	WebView content;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.knowledgebaseactivity);
		
		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);
		
		content = (WebView) findViewById(R.id.knowledgebase_holder);
	}
	
	@Override
	public void onClick(View v) {
		if(v == navigation)
			finish();
	}
}
