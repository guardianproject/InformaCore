package org.witness.informacam.storage;

import java.io.File;

import org.witness.informacam.InformaCam;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

public class InformaCamMediaScanner implements MediaScannerConnectionClient {	
	private MediaScannerConnection msc;
	private File f;
	private Activity a;
	
	public interface OnMediaScannedListener {
		public void onMediaScanned(Uri uri);
	}
	
	public InformaCamMediaScanner(Activity a, File f) {
		this.f = f;
		this.a = a;
		msc = new MediaScannerConnection(InformaCam.getInstance().a, this);
		msc.connect();
	}
	
	@Override
	public void onMediaScannerConnected() {
		msc.scanFile(f.getAbsolutePath(), null);
	}

	@Override
	public void onScanCompleted(String path, Uri uri) {
		((OnMediaScannedListener) a).onMediaScanned(uri);
	}
	
	public static Uri getUriFromFile(Context context, File file) {
		Uri uri = null;
		
		ContentResolver cr = context.getContentResolver();
		Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {BaseColumns._ID}, MediaStore.Images.Media.DATA + "=?", new String[] {file.getAbsolutePath()}, null);
		if(c != null && c.moveToFirst()) {
			uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(c.getLong(c.getColumnIndex(BaseColumns._ID))));
			c.close();
		}
		return uri;
	}
	
	public static void doScanForDeletion(final Context c, final File file) {
		MediaScannerConnection.scanFile(c, new String[] {file.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
			
			@Override
			public void onScanCompleted(String path, Uri uri) {
				file.delete();
				c.getContentResolver().delete(uri, null, null);
			}
		});
	}

}