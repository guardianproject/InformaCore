package org.witness.informacam.app;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.witness.informacam.R;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity implements OnClickListener {
	private Button loginButton;
	private EditText loginPasswordHolder;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		initLayout();
	}
	
	private void initLayout() {
		setContentView(R.layout.loginactivity);
		
		loginButton = (Button) findViewById(R.id.loginButton);
		loginButton.setOnClickListener(this);
		
		loginPasswordHolder = (EditText) findViewById(R.id.loginPasswordHolder);
		loginPasswordHolder.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
		loginPasswordHolder.setTransformationMethod(new PasswordTransformationMethod());
	}

	@Override
	public void onClick(View v) {
		if(v == loginButton) {
			if(validatePassword(this, loginPasswordHolder.getText().toString())) {
				PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Settings.Keys.CURRENT_LOGIN, loginPasswordHolder.getText().toString()).commit();
				finish();
			} else {
				loginPasswordHolder.setText("");
				Toast.makeText(this, getString(R.string.error_password_mismatch), Toast.LENGTH_LONG).show();
			}
		}
	}
	
	public static boolean validatePassword(Context c, String pwd) {
		DatabaseHelper dh = new DatabaseHelper(c);
		boolean result = false;
		
		try {
			SQLiteDatabase db = dh.getReadableDatabase(pwd);
			db.close();
			result = true;
		} catch(SQLiteException e) {}
		
		dh.close();
		return result;
	}
}
