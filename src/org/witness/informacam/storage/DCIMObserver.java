package org.witness.informacam.storage;

import java.util.List;
import java.util.Vector;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.j3m.IDCIMDescriptor;
import org.witness.informacam.utils.Constants.App.Storage;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

public class DCIMObserver {
	private final static String LOG = Storage.LOG;

	IDCIMDescriptor dcimDescriptor;
	List<ContentObserver> observers;
	InformaCam informaCam = InformaCam.getInstance();

	Handler h;
	private Context mContext;
	
	public DCIMObserver(Context context) {
		
		mContext = context;
		
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
			mContext.getContentResolver().registerContentObserver(((Observer) o).authority, false, o);
		}

		dcimDescriptor = new IDCIMDescriptor();
		dcimDescriptor.startSession();

		Log.d(LOG, "DCIM OBSERVER INITED");
	}

	public void destroy() {
		dcimDescriptor.stopSession();
			
			
     for(ContentObserver o : observers) {
    	 mContext.getContentResolver().unregisterContentObserver(o);
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
			Log.d(LOG, "ON CHANGE CALLED (no URI)");
			onChange(selfChange, null);

		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			if(uri != null) {
				Log.d(LOG, "ON CHANGE CALLED (with URI!)");
			}
			
			boolean isThumbnail = false;

			if(
					authority.equals(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI) || 
					authority.equals(MediaStore.Images.Thumbnails.INTERNAL_CONTENT_URI) ||
					authority.equals(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI) || 
					authority.equals(MediaStore.Video.Thumbnails.INTERNAL_CONTENT_URI) 
					) {
				isThumbnail = true;
			}

			dcimDescriptor.addEntry(authority, isThumbnail);
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}

	}

}
