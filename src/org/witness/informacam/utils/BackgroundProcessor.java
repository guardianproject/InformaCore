package org.witness.informacam.utils;

import java.util.ArrayList;
import java.util.List;

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
	
	private static BackgroundProcessor backgroundProcessor;
	
	private final static String LOG = Background.LOG;
	private final IBinder binder = new LocalBinder();
	
	List<Thread> tasks;
	
	public class LocalBinder extends Binder {
		public BackgroundProcessor getService() {
			return BackgroundProcessor.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		informaCam = InformaCam.getInstance();
		
		tasks = new ArrayList<Thread>();
		
		backgroundProcessor = this;
		sendBroadcast(new Intent().setAction(Actions.ASSOCIATE_SERVICE).putExtra(Codes.Keys.SERVICE, Codes.Routes.BACKGROUND_PROCESSOR));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		sendBroadcast(new Intent().putExtra(Codes.Keys.SERVICE, Codes.Routes.BACKGROUND_PROCESSOR).setAction(Actions.DISASSOCIATE_SERVICE));
	}
	
	public static BackgroundProcessor getInstance() {
		return backgroundProcessor;
	}
	
	public void addTask(Thread thread) {
		tasks.add(thread);
	}

}
