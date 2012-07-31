package org.witness.informacam.app;

import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class MediaManagerActivity extends Activity implements OnClickListener, OnRoutedListener {
	
	Handler h;
	
	ImageButton navigation;
	
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
	}
	
	private void getMedia() {
		
	}
	
	@Override
	public void onClick(View v) {
		if(v == navigation)
			finish();
		
	}

	@Override
	public void onRouted() {
		h = new Handler();
		h.post(new Runnable() {
			@Override
			public void run() {
				getMedia();
			}
		});
		
	}

}
