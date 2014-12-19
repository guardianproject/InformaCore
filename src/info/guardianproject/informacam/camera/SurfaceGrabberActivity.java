package info.guardianproject.informacam.camera;

import java.io.IOException;
import java.util.List;

import org.witness.informacam.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class SurfaceGrabberActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, PictureCallback {
	
	Button button;

	SurfaceView surfaceView;
	SurfaceHolder holder;
	Camera camera;
	CameraInfo cameraInfo;
	
	boolean mPreviewing;

	private final static String LOG = "Camera";

	private int mRotation = -1;
	boolean isVideoMode = false;
	
	private TextView tvProgress;
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getLayout());
		
		button = (Button) findViewById(R.id.surface_grabber_button);
		button.setOnClickListener(this);
		
		
		tvProgress = (TextView) findViewById(R.id.surface_grabber_progress);
		
		surfaceView = (SurfaceView) findViewById(R.id.surface_grabber_holder);
		holder = surfaceView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		surfaceView.setOnClickListener(this);
				
	}

	public void updateText (String text)
	{
		tvProgress.setText(text);
	
	}
	
	protected int getLayout()
	{
		return R.layout.camera;
	}
	
	protected int getCameraDirection()
	{
		return CameraInfo.CAMERA_FACING_BACK;
	}
	
	/**
     * Whether or not we can default to "other" direction if our preferred facing camera can't be opened
     * @return true to try camera facing other way, false otherwise
     */
    protected boolean canUseOtherDirection()
    {
            return false;
    }

	
	@Override
	public void onResume() {
		super.onResume();

		if (!tryCreateCamera(getCameraDirection()))
        {
                if (!canUseOtherDirection() || !tryCreateCamera(getOtherDirection(getCameraDirection())))
                {
                        finish();
                        return;
                }
        }

		if(camera == null)
			finish();
		
		mRotation = setCameraDisplayOrientation();
	}

	private int getOtherDirection(int facing)
	{
		return (facing == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK;
	}
	
	private boolean tryCreateCamera(int facing)
	{
	     Camera.CameraInfo info = new Camera.CameraInfo();
	     for (int nCam = 0; nCam < Camera.getNumberOfCameras(); nCam++)
	     {
		     Camera.getCameraInfo(nCam, info);
		     if (info.facing == facing)
		     {
		    	 camera = Camera.open(nCam);
		    	 try {
					camera.reconnect();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    	 
		    	 cameraInfo = info;
		    //	 Size size = choosePictureSize(camera.getParameters().getSupportedPictureSizes());

		    	 Camera.Parameters params = camera.getParameters();
				 params.setPictureFormat(ImageFormat.JPEG);
					//params.setPictureSize(size.width,size.height);
					//params.setJpegThumbnailSize(128,128);
//					params.setPresurfaceViewSize(size.width/2,size.height/2); 
				 
				 if (this.getCameraDirection() == CameraInfo.CAMERA_FACING_BACK)
				 {
					 params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
					 
				 }
									
				camera.setParameters(params);
				camera.startPreview();

		    	 return true;
		     }
	     }
	     return false;
	}
	
	@Override
	public void onPause() {
		if(camera != null)
		{
			camera.release();
			camera = null;
		}

		super.onPause();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		camera.startPreview();
		mPreviewing = true;
	}

	protected Size choosePictureSize(List<Size> localSizes)
	{
		Size size = null;
		
		for(Size sz : localSizes) {
			if(sz.width > 640 && sz.width <= 1024)
				size = sz;
			
			if(size != null)
				break;
		}
		
		if(size == null)
			size = localSizes.get(localSizes.size() - 1);
		return size;
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {

	        
			camera.setPreviewDisplay(holder);
			
		} catch(IOException e) {
			Log.e(LOG, "error setting preview display",e);
			
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void onClick(View view) {
		if(view == button && mPreviewing) {
			mPreviewing = false;
			camera.takePicture(null, null, this);
		}
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		
		try
		{
			String pathToData = "";
			//data, new File(pathToData));
				
			surfaceView.post(new Runnable()
			{
				@Override
				public void run() {
					resumePreview();
				}
			});
		}
		catch (Exception ioe)
		{
			Log.e(LOG,"error saving picture to iocipher",ioe);
		}
	}

	protected void resumePreview()
	{
		if (!mPreviewing)
		{
			camera.startPreview();
			mPreviewing = true;
		}
	}
	
	public int setCameraDisplayOrientation() 
	{        
	     if (camera == null || cameraInfo == null)
	     {
	         return -1;      
	     }

	     WindowManager winManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
	     int rotation = winManager.getDefaultDisplay().getRotation();

	     int degrees = 0;

	     switch (rotation) 
	     {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }

	     int result;
	     if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
	     {
	         result = (cameraInfo.orientation + degrees) % 360;
	         result = (360 - result) % 360;  // compensate the mirror
	     } else {  // back-facing
	         result = (cameraInfo.orientation - degrees + 360) % 360;
	     }
	     camera.setDisplayOrientation(result);
	     
	     return result;
	}
	
	/**
	 * This method configures the camera with a set of defaults for brightness,
	 * flash, camera mode, and picture sizes.
	 */
	private void setCameraDefaults()
	{
	    Camera.Parameters params = camera.getParameters();

	    // Supported picture formats (all devices should support JPEG).
	    List<Integer> formats = params.getSupportedPictureFormats();

	    if (formats.contains(ImageFormat.JPEG))
	    {
	        params.setPictureFormat(ImageFormat.JPEG);
	        params.setJpegQuality(100);
	    }
	    else
	        params.setPictureFormat(PixelFormat.RGB_565);

	    // Now the supported picture sizes.
	    List<Size> sizes = params.getSupportedPictureSizes();
	    Camera.Size size = sizes.get(sizes.size()-1);
	    params.setPictureSize(size.width, size.height);

	    // Set the brightness to auto.
	    params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

	    // Set the flash mode to auto.
	    params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);

	    // Set the scene mode to auto.
	    params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

	    // Lastly set the focus to auto.
	    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

	    camera.setParameters(params);
	}
}