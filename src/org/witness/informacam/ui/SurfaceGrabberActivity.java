package org.witness.informacam.ui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Models;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
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
	
	private InformaCam informaCam = InformaCam.getInstance();

	private final static String LOG = App.ImageCapture.LOG;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_surface_grabber);

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
				Log.d(LOG, "w: " + sz.width + ", h: " + sz.height);
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
			Log.e(LOG, e.toString());
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
		String pathToData = System.currentTimeMillis() + "_baseImage";
		if(informaCam.ioService.saveBlob(data, new File(pathToData))) {
			try {
				informaCam.user.put(Models.IUser.PATH_TO_BASE_IMAGE, pathToData);
				informaCam.user.hasBaseImage = true;
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			setResult(Activity.RESULT_OK);
			finish();
		}
	}

}