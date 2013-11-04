package org.witness.informacam.utils;

import org.witness.informacam.InformaCam;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;

public class ActivityPipe {
	private InformaCam informaCam;
	private Activity activity;
	
	public Intent responseObject = null;
	public int requestCode, responseCode;
	private PipeRunnable pipeRunnable = null;
	
	public ActivityPipe(Intent intent, int requestCode) {
		informaCam = InformaCam.getInstance();
		activity = informaCam.requestFocus();
		
		if(activity == null) {
			return;
		}
		
		activity.startActivityForResult(intent, requestCode);
	}
	
	public void setPipeRunnable(PipeRunnable pipeRunnable) {
		this.pipeRunnable = pipeRunnable;
	}
	
	public void setResponseObject(int requestCode, int responseCode, Intent responseObject) {
		this.responseObject = responseObject;
		this.requestCode = requestCode;
		this.responseCode = responseCode;
		
		if(pipeRunnable != null) {
			(new Handler()).post(pipeRunnable);
		}
	}
	
	public static class PipeRunnable implements Runnable {
		protected final Intent responseObject;
		protected final int requestCode, responseCode;
		
		public PipeRunnable(ActivityPipe activityPipe) {
			responseObject = activityPipe.responseObject;
			responseCode = activityPipe.responseCode;
			requestCode = activityPipe.requestCode;
		}
		
		@Override
		public void run() {}
	}
}
