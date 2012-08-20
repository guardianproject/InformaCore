package org.witness.informacam.transport;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.informa.Informa.Owner;
import org.witness.informacam.j3m.J3MDescriptor;
import org.witness.informacam.j3m.J3MDescriptor.J3MListener;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.IOCipherService;
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

public class UploaderService extends Service implements J3MListener {
	private final IBinder binder = new LocalBinder();
	private static UploaderService uploaderService; 
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	SharedPreferences sp;
	Queue<J3MDescriptor> queue;
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	public class LocalBinder extends Binder {
		public UploaderService getService() {
			return UploaderService.this;
		}
	}
	
	public UploaderService getInstance() {
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
						J3MDescriptor j3m = queue.peek();
						if(j3m == null) {
							monitor();
							break;
						}
						
						requestTicket(j3m);
						
					} while(!queue.isEmpty());
					
					monitor();
				}
			});
	
	private void requestTicket(J3MDescriptor j3m) {
		Map<String, Object> postData = new HashMap<String, Object>();
		postData.put(Uploader.Keys.CLIENT_PGP, j3m.clientPGPFingerprint);
		postData.put(Uploader.Keys.TIMESTAMP_CREATED, j3m.timestampCreated);
		postData.put(Uploader.Keys.MEDIA_TYPE, j3m.mediaType);
		postData.put(Uploader.Keys.BYTES_EXPECTED, j3m.totalBytesExpected);
		
		
		try {
			HttpUtility.executeHttpsPost(this, j3m.serverUrl, postData, null);
		} catch (InterruptedException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		} catch (ExecutionException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	@Override
	public void onCreate() {
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		queueMonitor.start();
		initUploads();
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
	
	private void addToQueue(J3MDescriptor j3m) {
		if(queue == null)
			queue = new LinkedList<J3MDescriptor>();
		
		queue.add(j3m);
	}
	
	private void initUploads() {
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		String clientPGPFingerprint = null;
		
		dh.setTable(db, Tables.Keys.KEYRING);
		Cursor client = dh.getJoinedValue(
				db, 
				new String[] {PGP.Keys.PGP_FINGERPRINT}, 
				new String[] {Tables.Keys.KEYRING, Tables.Keys.SETUP},
				new String[] {PGP.Keys.PGP_KEY_ID, PGP.Keys.PGP_KEY_ID},
				null);
		if(client != null && client.moveToFirst()) {
			clientPGPFingerprint = client.getString(client.getColumnIndex(PGP.Keys.PGP_FINGERPRINT));
			client.close();
		}
		
		dh.setTable(db, Tables.Keys.MEDIA);
		Cursor uploads = dh.getValue(db, null, Media.Keys.STATUS, Media.Status.IDLE);
		if(uploads != null && uploads.moveToFirst()) {
			while(!uploads.isAfterLast()) {
				J3MDescriptor j3m = new J3MDescriptor(this);
				j3m.originalHash = uploads.getString(uploads.getColumnIndex(Media.Keys.ORIGINAL_HASH));
				
				dh.setTable(db, Tables.Keys.TRUSTED_DESTINATIONS);
				Cursor server = dh.getValue(db, new String[] {TrustedDestination.Keys.URL}, BaseColumns._ID, uploads.getLong(uploads.getColumnIndex(Media.Keys.TRUSTED_DESTINATION_ID)));
				if(server != null && server.moveToFirst()) {
					
					j3m.clientPGPFingerprint = clientPGPFingerprint;
					j3m.setFile(uploads.getString(uploads.getColumnIndex(Media.Keys.LOCATION_OF_SENT)));
					j3m.mediaType = uploads.getInt(uploads.getColumnIndex(Media.Keys.TYPE));
					j3m.serverUrl = server.getString(server.getColumnIndex(TrustedDestination.Keys.URL));
					j3m.timestampCreated = uploads.getLong(uploads.getColumnIndex(Media.Keys.TIME_CAPTURED));
					
					j3m.changeStatus(Media.Status.UPLOADING);
					addToQueue(j3m);
					
					server.close();
				} else {
					// Has no server yet, set status to not uploadable
					j3m.changeStatus(Media.Status.NEVER_SCHEDULED_FOR_UPLOAD);
				}
				uploads.moveToNext();
			}
			
			uploads.close();
		}
	}

	@Override
	public void onStatusChanged(J3MDescriptor j3m, int newStatus) {
		ContentValues cv = new ContentValues();
		cv.put(Media.Keys.STATUS, newStatus);
		
		dh.setTable(db, Tables.Keys.MEDIA);
		db.update(dh.getTable(), cv, Media.Keys.ORIGINAL_HASH, new String[] {j3m.originalHash});
		
	}

}
