package org.witness.informacam.app.mods;

import org.witness.informacam.R;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.EditText;

public class InformaEditText extends EditText {

	public InformaEditText(Context context) {
		super(context);
		setLayout();
	}

	
	public InformaEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		setLayout();
	}
	
	public void setLayout() {
		setBackgroundResource(R.drawable.edit_text_background);
		setTextColor(Color.WHITE);
		setHintTextColor(0xccffffff);
	}

}
