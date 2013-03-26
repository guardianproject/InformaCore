package org.witness.informacam.storage;

import java.util.List;
import java.util.Vector;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.models.IDCIMDescriptor;
import org.witness.informacam.utils.models.IDCIMEntry;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

@SuppressLint("NewApi")
public class DCIMObserver {
	private final static String LOG = Storage.LOG;
	
	IDCIMDescriptor dcimDescriptor;
	List<ContentObserver> observers;
	InformaCam informaCam = InformaCam.getInstance();
	
	Handler h;
	Context c;
	
	public DCIMObserver(Context c) {
		this.c = c;
		h = new Handler();
		
		observers = new Vector<ContentObserver>();
		observers.add(new Observer(h, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Images.Media.INTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Video.Media.EXTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Video.Media.INTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Images.Thumbnails.INTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI));
		observers.add(new Observer(h, MediaStore.Video.Thumbnails.INTERNAL_CONTENT_URI));
		
		for(ContentObserver o : observers) {
			c.getContentResolver().registerContentObserver(((Observer) o).authority, false, o);
		}
		
		dcimDescriptor = new IDCIMDescriptor();
		dcimDescriptor.startSession();
		
		Log.d(LOG, "DCIM OBSERVER INITED");
	}
	
	public void destroy() {
		dcimDescriptor.stopSession();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				for(IDCIMEntry entry : dcimDescriptor.thumbnails) {
					informaCam.ioService.delete(entry.fileName, Type.FILE_SYSTEM);
					informaCam.ioService.delete(entry.uri, Type.CONTENT_RESOLVER);
				}
				
				dcimDescriptor.thumbnails = null;
				
				if(informaCam.ioService.saveBlob(dcimDescriptor, new info.guardianproject.iocipher.File(IManifest.DCIM))) {
					Bundle data = new Bundle();
					data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.DCIM.STOP);
					
					Message message = new Message();
					message.setData(data);
					
					try {
						((InformaCamEventListener) informaCam.a).onUpdate(message);
					} catch(ClassCastException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
			}
		}).start();
		
		
		for(ContentObserver o : observers) {
			c.getContentResolver().unregisterContentObserver(o);
		}
		
		Log.d(LOG, "DCIM OBSERVER STOPPED");
	}
	
	class Observer extends ContentObserver {
		Uri authority;
		
		public Observer(Handler handler, Uri authority) {
			super(handler);
			this.authority = authority;
		}
		
		@Override
		public void onChange(boolean selfChange) {
			this.onChange(selfChange, null);
			
		}
		
		@Override
		public void onChange(boolean selfChange, Uri uri) {
			boolean isThumbnail = false;
			
			if(
					authority.equals(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI) || 
					authority.equals(MediaStore.Images.Thumbnails.INTERNAL_CONTENT_URI) ||
					authority.equals(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI) || 
					authority.equals(MediaStore.Video.Thumbnails.INTERNAL_CONTENT_URI) 
			) {
				isThumbnail = true;
			}
			
			dcimDescriptor.addEntry(authority, c, isThumbnail);
		}
		
		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}
		
	}

}
