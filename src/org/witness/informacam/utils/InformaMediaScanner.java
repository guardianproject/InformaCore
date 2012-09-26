package org.witness.informacam.utils;

import java.io.File;

import org.witness.informacam.utils.Constants.Media;

import android.app.Activity;
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
		Log.d(Media.LOG, "media scanner started...");
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
		Log.d(Media.LOG, "media scanner DONE...");
	}

}
