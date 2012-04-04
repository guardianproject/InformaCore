package org.witness.mods;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

public class InformaTextView extends TextView {

	public InformaTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setTextColor(Color.WHITE);
		setTextSize(14);
	}
	
	public InformaTextView(Context context) {
		super(context);
		setTextColor(Color.WHITE);
		setTextSize(14);
	}

}
