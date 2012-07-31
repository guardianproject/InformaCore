package org.witness.informacam.app.mods;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

public class InformaButton extends Button {

	public InformaButton(Context context) {
		super(context);
		
	}
	
	public InformaButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		
	}
	
	@Override
	public void setText(CharSequence text, BufferType type) {
		super.setText(text.toString().toUpperCase(), BufferType.NORMAL);
	}

}
