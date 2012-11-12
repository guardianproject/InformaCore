package org.witness.informacam.app;

import android.app.Activity;
import android.widget.Toast;

public class ErrorHandler {
	public static void show(Activity a, String msg) {
		Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
	}
}
