package org.witness.informacam.app.mods;

import org.witness.informacam.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class InformaEditTextAlert extends AlertDialog {
	Context context;
	LayoutInflater li;
	View inner;
	TextView title, prompt;
	
	Object obj;
	InformaEditTextAlert ieta;
	
	Activity a;
	
	public interface AlertInputListener {
		public void onSubmit(Object obj, String input);
	}
	
	public InformaEditTextAlert(final Activity a, final Object obj) {
		super(a);
		this.a = a;
		this.obj = obj;
		
		li = LayoutInflater.from(a);
		inner = li.inflate(R.layout.edittext_alert, null);
		this.setView(inner);
		
		title = (TextView) inner.findViewById(R.id.edittext_alert_title);
		
		prompt = (TextView) inner.findViewById(R.id.edittext_alert_prompt);
		
		final EditText input = (EditText) inner.findViewById(R.id.edittext_alert_holder);
		
		Button submit = (Button) inner.findViewById(R.id.edittext_alert_submit);
		submit.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				((AlertInputListener) a).onSubmit(obj, input.getText().toString());
				ieta.dismiss();
			}
		});
		
		ieta = this;
	}
	
	public InformaEditTextAlert(Context context) {
		super(context);
	}
	
	@Override
	public void setTitle(CharSequence t) {
		title.setText(t);
	}
	
	@Override
	public void setMessage(CharSequence p) {
		prompt.setText(p);
	}

}
