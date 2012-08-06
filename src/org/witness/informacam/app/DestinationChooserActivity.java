package org.witness.informacam.app;

import java.util.ArrayList;

import org.witness.informacam.R;
import org.witness.informacam.app.mods.Selections;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;

public class DestinationChooserActivity  extends Activity implements OnClickListener {
	ListView keyChooser;
	Button keyChooser_ok;
	ArrayList<Selections> keys;
	Handler h;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.destinationchooseractivity);
		
		keyChooser = (ListView) findViewById(R.id.keyChooser);
		keyChooser_ok = (Button) findViewById(R.id.keyChooser_ok);
		keyChooser_ok.setOnClickListener(this);
		keys = new ArrayList<Selections>();
		
		h = new Handler();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		
	}

}
