package org.witness.informacam.utils;

import java.util.concurrent.LinkedBlockingQueue;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App.Background;

import android.util.Log;

@SuppressWarnings("serial")
public class BackgroundProcessor extends LinkedBlockingQueue<BackgroundTask> implements Runnable {
	InformaCam informaCam = InformaCam.getInstance();
	
	BackgroundTask currentTask = null;
	BackgroundTask onBatchComplete = null;
	
	boolean shouldRun = true;
	boolean batchFilled = false;
	
	private final static String LOG = Background.LOG;
	
	public void addTask(BackgroundTask task) {
		add(task);
	}
	
	public void setOnBatchComplete(BackgroundTask onBatchComplete) {
		this.onBatchComplete = onBatchComplete;
	}
	
	public void stop() {
		add(onBatchComplete);
		batchFilled = true;
	}

	@Override
	public void run() {
		while(shouldRun) {
			try {
				while((currentTask = take()) != null) {
					if(currentTask.onStart()) {
						currentTask.onStop();
					}
				}
			} catch (InterruptedException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			if(batchFilled && size() == 0) {
				shouldRun = false;
			}
		}
	}

}
