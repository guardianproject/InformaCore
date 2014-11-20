package info.guardianproject.informacam.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.witness.informacam.R;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class SecureVideoRecorderActivity extends Activity implements Callback {

    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView;
    public MediaRecorder mrec;
    private Camera mCamera;
    private String mPath;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        surfaceView = (SurfaceView) findViewById(R.id.surface_grabber_holder);
        mCamera = Camera.open();
        
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
     
        Button btn = (Button)findViewById(R.id.surface_grabber_button);
        btn.setOnClickListener(new OnClickListener ()
        {

			@Override
			public void onClick(View v) {
				
				try
				{
					if (mrec == null)
						startRecording();
					else					
						stopRecording();
					
				}
				catch (Exception e)
				{
					Log.d("VideoCamera","error doing camera",e);
				}
			}
        	
        });
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      // ignore orientation/keyboard change
      super.onConfigurationChanged(newConfig);
    }
    

    @Override
    protected void onDestroy() {
        stopRecording();
        super.onDestroy();
    }


    protected void startRecording() throws IOException
    {
        if(mCamera==null)
            mCamera = Camera.open();
        
         Date date=new Date();
         mPath ="/rec"+date.toString().replace(" ", "_").replace(":", "_")+".ts";
         
        mrec = new MediaRecorder(); 

        mCamera.lock();
        mCamera.unlock();

        mrec.setCamera(mCamera);    
        mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        
        //this sets the streaming format "TS"
        mrec.setOutputFormat(/*MediaRecorder.OutputFormat.OUTPUT_FORMAT_MPEG2TS*/8);
        
        
        
        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
 
        int width=640, height=480;
        int frameRate = 30;
        mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        
        mrec.setVideoSize(width, height);
        mrec.setVideoFrameRate(frameRate);
        mrec.setVideoEncodingBitRate(cpHigh.videoBitRate);
      
        /**
        mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
        mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
        mrec.setAudioEncodingBitRate(cpHigh.audioBitRate);
        mrec.setAudioChannels(1);
        mrec.setAudioSamplingRate(cpHigh.audioSampleRate);
        */
        
        mrec.setPreviewDisplay(surfaceHolder.getSurface());
 
        
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mrec.setOutputFile(pipe[1].getFileDescriptor());
        
        AutoCloseInputStream acis = new AutoCloseInputStream(pipe[0]);
        File file = new File(mPath);        
        new PipeFeeder(acis,new FileOutputStream(file)).start();
        
        mrec.prepare();
        mrec.start();

        
    }

    
    protected void stopRecording() {

        if(mrec!=null)
        {    
    		mrec.stop();
    		
    		releaseMediaRecorder();
    		
    		releaseCamera();
    		
			setResult(Activity.RESULT_OK, new Intent().putExtra("path", mPath));

			finish();
        }
    }

    private void releaseMediaRecorder() {

        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
            mrec = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {      

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {       

        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            mCamera.setParameters(params);
            Log.i("Surface", "Created");
        }
        else {
            Toast.makeText(getApplicationContext(), "Camera not available!",
                    Toast.LENGTH_LONG).show();

            finish();
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	

    }
}
