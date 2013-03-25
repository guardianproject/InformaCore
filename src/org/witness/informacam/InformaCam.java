package org.witness.informacam;

import org.witness.informacam.utils.Constants.App;


import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class InformaCam extends Application {	

	public void onCreate() {
		super.onCreate();
		Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler());
	}
	
	private void showCrashMonitor() {
		Log.d(App.LOG, "YO U GOT FC'D");
		this.sendBroadcast(new Intent().setAction(App.Main.FORCE_CLOSE_REPORTED));
		
	}
	
	class TopExceptionHandler implements Thread.UncaughtExceptionHandler {
		private Thread.UncaughtExceptionHandler ueh;

		TopExceptionHandler() { // Constructor
			this.ueh = Thread.getDefaultUncaughtExceptionHandler();
		}
	
		public void uncaughtException(Thread t, Throwable e) {
			
			showCrashMonitor();
			
		}
	}

}
