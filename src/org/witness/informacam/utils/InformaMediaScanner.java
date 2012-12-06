package org.witness.informacam.utils;

import java.io.File;

import org.witness.informacam.utils.Constants.Storage;

import android.app.Activity;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.util.Log;

public class InformaMediaScanner implements MediaScannerConnectionClient {
	private MediaScannerConnection msc;
	private File f;
	private Activity a;
	
	public interface OnMediaScannedListener {
		public void onMediaScanned(Uri uri);
	}
	
	public InformaMediaScanner(Activity a, File f) {
		this.f = f;
		this.a = a;
		msc = new MediaScannerConnection(a, this);
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
