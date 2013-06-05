package org.witness.informacam.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.App.Background;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BackgroundProcessor extends Service {
	InformaCam informaCam;
	
	public class BackgroundTask {
		public int type;
		public String pathToData;
		
		public BackgroundTask(int type, String pathToData) {
			this.type = type;
			this.pathToData = pathToData;
		}
	}
	
	private static BackgroundProcessor backgroundProcessor;
	
	private final static String LOG = Background.LOG;
	private final IBinder binder = new LocalBinder();
		
	public class LocalBinder extends Binder {
		public BackgroundProcessor getService() {
			return BackgroundProcessor.this;
		}
	}
	
	public Object doTask(final BackgroundTask task) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<Object> result = ex.submit(new Callable<Object>() {
			

			@Override
			public Object call() throws Exception {
				if(task.type == Codes.Tasks.ANALYZE_MEDIA) {
					
				}
				
				return null;
			}
		});
		
		
		try {
			return result.get();
		} catch (InterruptedException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (ExecutionException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		informaCam = InformaCam.getInstance();
				
		backgroundProcessor = this;
		sendBroadcast(new Intent()
			.setAction(Actions.ASSOCIATE_SERVICE)
			.putExtra(Codes.Keys.SERVICE, Codes.Routes.BACKGROUND_PROCESSOR)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, android.os.Process.myPid()));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		sendBroadcast(new Intent()
			.putExtra(Codes.Keys.SERVICE, Codes.Routes.BACKGROUND_PROCESSOR)
			.setAction(Actions.DISASSOCIATE_SERVICE)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, android.os.Process.myPid()));
	}
	
	public static BackgroundProcessor getInstance() {
		return backgroundProcessor;
	}

}
