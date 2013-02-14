package org.witness.informacam.app;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.R;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.content.ContentValues;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class SurfaceGrabberActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, PictureCallback {
	Button button;

	SurfaceView view;
	SurfaceHolder holder;
	Camera camera;
	Size size = null;


	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.surfacegrabberactvitiy);

		button = (Button) findViewById(R.id.surface_grabber_button);
		button.setOnClickListener(this);

		view = (SurfaceView) findViewById(R.id.surface_grabber_holder);
		holder = view.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);		
	}

	@Override
	public void onResume() {
		super.onResume();

		camera = Camera.open();

		if(camera == null)
			finish();
	}

	@Override
	public void onPause() {
		if(camera != null)
			camera.release();

		super.onPause();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		camera.startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			camera.setPreviewDisplay(holder);
			List<Size> localSizes = camera.getParameters().getSupportedPictureSizes();
			
			for(Size sz : localSizes) {
				Log.d(App.LOG, "w: " + sz.width + ", h: " + sz.height);
				if(sz.width > 480 && sz.width <= 640)
					size = sz;
				
				if(size != null)
					break;
			}
			
			if(size == null)
				size = localSizes.get(localSizes.size() - 1);

			Camera.Parameters params = camera.getParameters();
			params.setPictureSize(size.width, size.height);
			params.setJpegQuality(80);
			params.setJpegThumbnailQuality(80);


			// TODO: set the camera image size that is uniform and small.
			camera.setParameters(params);

		} catch(IOException e) {
			Log.e(App.LOG, e.toString());
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void onClick(View view) {
		if(view == button) {
			camera.takePicture(null, null, this);
		}
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		DatabaseHelper dh = DatabaseService.getInstance().getHelper();
		SQLiteDatabase db = DatabaseService.getInstance().getDb();
		
		ContentValues cv = new ContentValues();
		cv.put(Device.Keys.BASE_IMAGE, Base64.encode(data, Base64.DEFAULT));
		cv.put(Informa.Keys.Device.DISPLAY_NAME, PreferenceManager.getDefaultSharedPreferences(this).getString(Informa.Keys.Device.DISPLAY_NAME, ""));

		dh.setTable(db, Tables.Keys.SETUP);
		if(db.insert(dh.getTable(), null, cv) > 0) {
			this.setResult(Activity.RESULT_OK);
			finish();
		}


	}

}
