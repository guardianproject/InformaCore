package org.witness.informacam.app;

import org.witness.informacam.R;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

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
		
		
	}
}
