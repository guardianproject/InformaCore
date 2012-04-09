package org.witness.ssc;

import android.app.Activity;
import android.os.Bundle;

public class FullStop extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		super.finish();
	}
}
