package org.witness.informacam.app.mods;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class InformaHeaderTextView extends TextView {
	public InformaHeaderTextView(Context context) {
		super(context);
	}
	
	public InformaHeaderTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override
	public void setText(CharSequence text, BufferType type) {
		super.setText(text.toString().toUpperCase(), BufferType.NORMAL);
	}

}
