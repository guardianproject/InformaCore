package info.guardianproject.informacam.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.view.View;

public class SecureCameraActivity extends SurfaceGrabberActivity {
	
	private final static String TAG = "SecureCamera";
	
	private String fileBasePath = null;
	private MediaRecorder recorder;
    private String fileVideoPath;
	private Handler handler = new Handler();

	private LocalServerSocket lSS;
	private LocalSocket sender;
	private LocalSocket receiver;
	private boolean keepStreaming = false;
	
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
	public void onResume() {
		super.onResume();
		
	
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
					Log.d(TAG,"error stop/start recording",e);
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
				addResultPath(fileSecurePicture.getAbsolutePath(),false);

				//now restart preview after a short delay
				handler.postDelayed(new Runnable ()
				{
					public void run ()
					{
						if (camera != null)
						{
							camera.startPreview();
							mPreviewing = true;
						}
					}
					
				},100);
			}
			else
			{
				addResultPath(fileSecurePicture.getAbsolutePath(),true);
				//is a thumb, so release the media recorder
	    		releaseMediaRecorder();
	    		
	    		//now add the video path
	    		addResultPath(fileVideoPath,false);
				

			}
			
		} catch (Exception e) {
			Log.e(TAG,"error capturing photo",e);
		}
		

	}
	
	protected void startRecording () throws IOException
	{
		updateText("preparing...");

		initRecording();
		
		handler.postDelayed(new Runnable ()
		{
			
			public void run ()
			{
				updateText(getString(R.string.recording_video_));
		     
				try
				{
					recorder.start();
				}
				catch (Exception e)
				{
					Log.e(TAG,"could not start video recroder",e);
					finish();
				}
			}
		},2000);
		
	}
	
	protected void initRecording() throws IOException
    {
		
		String fileExt = ".ts";//".3gp";//".mp4:l
        Date date=new Date();
        fileVideoPath = new File(fileBasePath,"rec"+date.toString().replace(" ", "_").replace(":", "_")+fileExt).getAbsolutePath();
        
        CamcorderProfile profile = null;
        if(CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)){
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        }else{ profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW); }
        
        recorder = new MediaRecorder(); 

        recorder.setOnInfoListener(new OnInfoListener ()
        {

			@Override
			public void onInfo(MediaRecorder arg0, int arg1, int arg2) {
				
				Log.d(TAG,"mediarecorder info: " + arg1 + "," + arg2);
			}
        	
        });
        
        recorder.setOnErrorListener(new OnErrorListener ()
        {

			@Override
			public void onError(MediaRecorder arg0, int arg1, int arg2) {
				Log.d(TAG,"mediarecorder error: " + arg1 + "," + arg2);
				
			}

        	
        });
        
        camera.unlock();
        recorder.setCamera(camera);    
        //recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        
        recorder.setOutputFormat(/*MediaRecorder.OutputFormat.OUTPUT_FORMAT_MPEG2TS*/8);
        //recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        //recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
                 
        if (fileExt.equals(".3gp"))
        {
	        recorder.setVideoFrameRate(20);
	        recorder.setVideoSize(352,288);        
	        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
        }
        else
        {
        	
        	recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        	
        	recorder.setVideoFrameRate(profile.videoFrameRate);
        	recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        	recorder.setVideoEncodingBitRate(profile.videoBitRate);
        	
        	
            
            //recorder.setAudioSamplingRate(24000); 
            //recorder.setAudioEncodingBitRate(64000);
            
            recorder.setMaxDuration(9600000); //4 hours
            	
        }

        recorder.setPreviewDisplay(holder.getSurface());

        recordViaLocalSocket();
        //recordViaLocalPipe();

        recorder.prepare();
    }
	
	/*
	private void recordViaLocalPipe () throws IOException
	{
		File file = new File(fileVideoPath);
		//RandomAccessFile fileRAF = new RandomAccessFile(file, "RWS");
     
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();       
        AutoCloseInputStream acis = new AutoCloseInputStream(pipe[0]);
                
        new PipeFeeder(acis,new FileOutputStream(file)).start();        
        recorder.setOutputFile(pipe[1].getFileDescriptor());
        
        //recorder.setOutputFile(fileRAF.getFD());
        
     
    }*/
	
	private void recordViaLocalSocket () throws IOException
	{
           
        lSS = new LocalServerSocket("ic" + new Date().getTime());
        receiver = new LocalSocket();
		receiver.connect(lSS.getLocalSocketAddress());	
		receiver.setSendBufferSize(1024);
		receiver.setReceiveBufferSize(1024);		
		
        sender = lSS.accept();
        sender.setSendBufferSize(1024);
        sender.setReceiveBufferSize(1024);
		
		new Thread ()
		{
			
			public void run ()
			{

				try {

					keepStreaming = true;
					
					InputStream in = receiver.getInputStream();
					OutputStream out = new FileOutputStream(new File(fileVideoPath));
					
					byte[] buf = new byte[1024];
					int len;

					int idx = 0;
					while (keepStreaming && (len = in.read(buf)) != -1)
					{
						out.write(buf, 0, len);
						idx += buf.length;
						Log.d(TAG,"writing to IOCipher at " + idx);
					}
					
					Log.d(TAG,"done streaming from localsocket");
				//	in.close();
					out.flush();
					out.close();
					
				} catch (IOException e) {
					Log.e(TAG, "Video stream capture exception", e);
				}
			}
		}.start();
		
        recorder.setOutputFile(sender.getFileDescriptor());
        

    }

    
    @Override
	public void onPause() {
		super.onPause();

    }
    
    
    private void addResultPath (String path, boolean isThumb)
    {
    
    	InformaCam informaCam = (InformaCam)getApplication();		
    	try {
			informaCam.ioService.getDCIMDescriptor().addEntry(path, isThumb, Storage.Type.IOCIPHER);
		} catch (InstantiationException e) {
			Log.e(TAG,"error",e);
		} catch (IllegalAccessException e) {
			Log.e(TAG,"error",e);
		}
	
	}


	protected void stopRecording() {

		updateText("");
		
        if(recorder!=null)
        {    
        	try
        	{

	    		//addResultPath(fileVideoPath);
	    		recorder.stop();
        		keepStreaming = false;

        		//releaseMediaRecorder();        		
        		camera.takePicture(null, null, this);
        		
        	}
        	catch (IllegalStateException ise)
        	{
        		Log.d(TAG,"problem stopping recorder",ise);
        	}
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
