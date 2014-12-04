package info.guardianproject.informacam.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Date;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.utils.Constants.App.Storage;

import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.view.View;

public class SecureCameraActivity extends SurfaceGrabberActivity {
	
	private final static String LOG = "SecureCamera";
	
	private String fileBasePath = null;
    public MediaRecorder recorder;
    private String fileVideoPath;
	private Handler handler = new Handler();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		fileBasePath = getIntent().getStringExtra("basepath");

		Intent intent = getIntent();
		if (intent != null)
		{
			String action = intent.getAction();
			
			if (action.equals(org.witness.informacam.utils.Constants.App.Camera.Intents.SECURE_CAMERA))
			{
				//default to still mode
				isVideoMode = false;
			}
			else if (action.equals(org.witness.informacam.utils.Constants.App.Camera.Intents.SECURE_CAMCORDER))
			{
				//default to video mode
				isVideoMode = true;
			}
		}
	}
	

	@Override
	public void onClick(View view) {
		if(view == button) {
			
			if (isVideoMode)
			{
				try {
					
					if (recorder == null)
						startRecording();
					else
						stopRecording ();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else
			{
				try
				{
					//take a still picture
					camera.takePicture(null, null, this);
				}
				catch (RuntimeException re)
				{
					releaseMediaRecorder();
					finish();
					//something is not working
					
				}
			}
		}
		else if (view == this.surfaceView)
		{
			
			//take a still picture
			camera.takePicture(null, null, this);
			
		}
	}

	@Override
	protected int getLayout()
	{
		return R.layout.camera;
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  // ignore orientation/keyboard change
	  super.onConfigurationChanged(newConfig);
	}
	
	@Override
	protected int getCameraDirection() {
		return CameraInfo.CAMERA_FACING_BACK;
	}

	@Override
	public void onPictureTaken(final byte[] data, Camera captureCamera) {		
		File fileSecurePicture;
		try {
			long mTime = System.currentTimeMillis();
			
			if (recorder != null) //recording video so do a thumb
			{
				fileSecurePicture = new File(fileVideoPath + ".thumb.jpg");
			}
			else
			{
				fileSecurePicture = new File(fileBasePath,"secure_" + mTime + ".jpg");
			}

			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fileSecurePicture));
			out.write(data);
			out.flush();
			out.close();

			if (recorder == null) //not a thumb!
			{
				addResultPath(fileSecurePicture.getAbsolutePath());

				//now restart preview after a short delay
				handler.postDelayed(new Runnable ()
				{
					public void run ()
					{
						camera.startPreview();
						mPreviewing = true;
					}
					
				},100);
			}
			else
			{
				//is a thumb, so release the media recorder
	    		releaseMediaRecorder();
	    		
	    		//now add the video path
	    		addResultPath(fileVideoPath);
				

			}
			
		} catch (Exception e) {
			Log.e(LOG,"error capturing photo",e);
		}
		

	}
	
	protected void startRecording() throws IOException
    {
		updateText("Recording video...");
		
        Date date=new Date();
        fileVideoPath = new File(fileBasePath,"rec"+date.toString().replace(" ", "_").replace(":", "_")+".ts").getAbsolutePath();
        
        recorder = new MediaRecorder(); 
        
        camera.lock();
        camera.unlock();

        recorder.setCamera(camera);    
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        
        //this sets the streaming format "TS"
        recorder.setOutputFormat(/*MediaRecorder.OutputFormat.OUTPUT_FORMAT_MPEG2TS*/8);
        
        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
 
        int width=640, height=480;
        int frameRate = 15;
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        
        recorder.setVideoSize(width, height);
        recorder.setVideoFrameRate(frameRate);
        recorder.setVideoEncodingBitRate(cpHigh.videoBitRate);
      
        /**
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
        recorder.setAudioEncodingBitRate(cpHigh.audioBitRate);
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(cpHigh.audioSampleRate);
        */
        
        recorder.setPreviewDisplay(holder.getSurface());
 
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        recorder.setOutputFile(pipe[1].getFileDescriptor());
        
        AutoCloseInputStream acis = new AutoCloseInputStream(pipe[0]);
        File file = new File(fileVideoPath);        
        new PipeFeeder(acis,new FileOutputStream(file)).start();
        
        recorder.prepare();
        recorder.start();

        
    }

    
    @Override
	public void onPause() {
		super.onPause();

    }
    
    
    private void addResultPath (String path)
    {
    
    	InformaCam informaCam = (InformaCam)getApplication();		
    	try {
			informaCam.ioService.getDCIMDescriptor().addEntry(path, false, Storage.Type.IOCIPHER);
		} catch (InstantiationException e) {
			Log.e(LOG,"error",e);
		} catch (IllegalAccessException e) {
			Log.e(LOG,"error",e);
		}
	
	}


	protected void stopRecording() {

		updateText("");
		
        if(recorder!=null)
        {    
        	
    		recorder.stop();
    		  
    		camera.takePicture(null, null, this);
    		
        }
    }

    private void releaseMediaRecorder() {

        if (recorder != null) {
            recorder.reset(); // clear recorder configuration
            recorder.release(); // release the recorder object
            recorder = null;
            camera.lock();
            camera.startPreview();
        }
    }

	
}
