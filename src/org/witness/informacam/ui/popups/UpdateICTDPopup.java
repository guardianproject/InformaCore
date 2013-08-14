package org.witness.informacam.ui.popups;

import org.witness.informacam.R;

import android.app.Activity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class UpdateICTDPopup extends Popup implements OnClickListener {
	protected Button ok, cancel;
	protected TextView warning;
	
	public UpdateICTDPopup(Activity a, String warningText) {
		super(a, R.layout.popup_update_ictd);
		ok = (Button) layout.findViewById(R.id.update_ok);
		ok.setOnClickListener(this);
		
		cancel = (Button) layout.findViewById(R.id.update_cancel);
		cancel.setOnClickListener(this);
		
		warning = (TextView) layout.findViewById(R.id.warning);
		warning.setText(warningText);
		
		Show();
	}

	@Override
	public void onClick(View v) {}

}
