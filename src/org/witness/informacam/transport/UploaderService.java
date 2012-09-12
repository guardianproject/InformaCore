package org.witness.informacam.transport;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.informa.Informa.Owner;
import org.witness.informacam.j3m.J3M;
import org.witness.informacam.j3m.J3M.J3MDescriptor;
import org.witness.informacam.j3m.J3M.J3MPackage;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Uploader;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class UploaderService extends Service {
	private final IBinder binder = new LocalBinder();
	private static UploaderService uploaderService; 
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	SharedPreferences sp;
	Queue<J3MPackage> queue;
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	public class LocalBinder extends Binder {
		public UploaderService getService() {
			return UploaderService.this;
		}
	}
	
	public static UploaderService getInstance() {
		return uploaderService;
	}
	
	private Thread queueMonitor = new Thread(
	new Runnable() {				
		@Override
		public void run() {
			if(queue.isEmpty())
				monitor();
			else
				upload();
		}
		
		private void monitor() {
			do {
				// wait...
			} while(queue.isEmpty());
			
			upload();
		}
		
		public void upload() {
			do {
				
				J3MPackage j3mPackage = queue.peek();
				Log.d(Transport.LOG, "HELLO! we have an upload! " + j3mPackage.toString());
				if(j3mPackage == null) {
					monitor();
					break;
				}
				
				requestTicket(j3mPackage);
				
			} while(!queue.isEmpty());
			
			monitor();
		}
	});
	
	private void requestTicket(J3MPackage j3mPackage) {
		Map<String, Object> postData = new HashMap<String, Object>();

		postData.put(Uploader.Keys.J3M, j3mPackage.j3m);
		Log.d(Transport.LOG, (j3mPackage.url + "\n" + postData.toString() + "\n" + j3mPackage.pkcs12Id));
		String result = HttpUtility.executeHttpsPost(this, j3mPackage.url, postData, j3mPackage.pkcs12Id, null);
		Log.d(Transport.LOG, "result:\n" + result);
		
	}
	
	@Override
	public void onCreate() {
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		queue = new LinkedList<J3MPackage>();
		//queueMonitor.start();
		uploaderService = this;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if(dh != null) {
			db.close();
			dh.close();
		}
		Log.d(Uploader.LOG, "uploader service destroyed.");
	}
	
	public void addToQueue(J3MPackage j3mPackage) {
		//queue.add(j3mPackage);
		requestTicket(j3mPackage);
	}	
}
