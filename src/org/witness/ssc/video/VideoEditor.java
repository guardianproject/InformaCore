/*
 * 
 * To Do:
 * 
 * Handles on SeekBar - Not quite right
 * Editing region, not quite right
 * RegionBarArea will be graphical display of region tracks, no editing, just selecting
 * 
 */

package org.witness.ssc.video;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.witness.informa.KeyChooser;
import org.witness.informa.ReviewAndFinish;
import org.witness.informa.Tagger;
import org.witness.informa.utils.VideoConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.io.ShellUtils;
import org.witness.informa.utils.io.ShellUtils.ShellCallback;
import org.witness.ssc.R;
import org.witness.ssc.image.ImageRegion;
import org.witness.ssc.image.detect.GoogleFaceDetection;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.utils.ObscuraConstants.VideoRegion;
import org.witness.ssc.video.InOutPlayheadSeekBar.InOutPlayheadSeekBarChangeListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class VideoEditor extends SherlockActivity implements OnClickListener, OnTouchListener, 
	SurfaceHolder.Callback, OnSeekBarChangeListener,
	OnErrorListener, OnCompletionListener, OnPreparedListener, 
	OnBufferingUpdateListener, OnVideoSizeChangedListener, OnSeekCompleteListener, OnInfoListener   {
	
	ActionBar ab;
	Menu menu;
	
	ImageButton playPause;
	
	Uri originalVideoUri;
	long firstTimestamp;
	int mDuration;
	int videoWidth, videoHeight;
	int timeNudgeOffset = 2;
	float vRatio;
	
	MediaPlayer mp;
	SurfaceView sv;
	SurfaceHolder holder;
	SeekBar seekBar;
	Timer t;
	TimerTask tt;
	Handler h = new Handler();
	
	Display screen;
	public int breakpointTop, breakpointLeft, breakpointWidth, breakpointHeight, breakpointOffset;
	
	boolean isPlaying, mCancelled, mAutoDetectEnabled;
	
	File fileExternDir;
	File redactSettingsFile;
	File saveFile;
	File recordingFile;
	VideoConstructor vc;
	
	ProgressDialog progressDialog;
	int completeActionFlag = -1;
	
	Bitmap obscuredBmp;
	Canvas obscuredCanvas;
	Paint obscuredPaint, selectedPaint;
	
	Bitmap bitmapCornerUL, bitmapCornerUR, bitmapCornerLL, bitmapCornerLR;
	long startTime = 0;
	float startX = 0;
	float startY = 0;
	float posX, posY;
	int currentNumFingers = 0;
	int regionCornerMode = 0;
	
	ImageView regionsView;
	public RelativeLayout breakpointHolder;
	
	private Vector<ObscureRegion> obscureRegions = new Vector<ObscureRegion>();
	private ObscureRegion activeRegion, regionInContext;
	
	Vibrator vibe;
	
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			Log.d(LOG, "messageType: " + msg);
			switch(msg.what) {
			case 0:
				progressDialog.dismiss();
				break;
			case 1:
				progressDialog.setMessage(msg.getData().getString("status"));
				progressDialog.setProgress(msg.getData().getInt("progress"));
				break;
			case 2:
				mCancelled = true;
				mAutoDetectEnabled = false;
				//killVideoProcessor();
				break;
			case 3:
				progressDialog.dismiss();
				//launchInforma();
				break;
			case 4:
				break;
			case 5:
				updateRegionDisplay();
				break;
			default:
				super.handleMessage(msg);
				
			}
		}
	};
	
	public static final String LOG = ObscuraConstants.TAG;
	
	Runnable runProcessVideo = new Runnable () {
		
		public void run ()
		{

			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
			wl.acquire();
	        
			try
			{
				if (vc == null)
					vc = new VideoConstructor(VideoEditor.this.getBaseContext());
	
				float sizeMult = .75f;
				int frameRate = 15;
				int bitRate = 300;
				String format = "mp4";
				
				ShellUtils.ShellCallback sc = new ShellUtils.ShellCallback ()
				{
					int total = 0;
					int current = 0;
					
					@Override
					public void shellOut(char[] shellout) {
						
						String line = new String(shellout);
						
						//progressDialog.setMessage(new String(msg));
						//Duration: 00:00:00.99,
						//time=00:00:00.00
						int idx1;
						String newStatus = null;
						int progress = 0;
						
						if ((idx1 = line.indexOf("Duration:"))!=-1)
						{
							int idx2 = line.indexOf(",", idx1);
							String time = line.substring(idx1+10,idx2);
							
							int hour = Integer.parseInt(time.substring(0,2));
							int min = Integer.parseInt(time.substring(3,5));
							int sec = Integer.parseInt(time.substring(6,8));
							
							total = (hour * 60 * 60) + (min * 60) + sec;
							
							newStatus = line;
							progress = 0;
						}
						else if ((idx1 = line.indexOf("time="))!=-1)
						{
							int idx2 = line.indexOf(" ", idx1);
							String time = line.substring(idx1+5,idx2);
							newStatus = line;
							
							int hour = Integer.parseInt(time.substring(0,2));
							int min = Integer.parseInt(time.substring(3,5));
							int sec = Integer.parseInt(time.substring(6,8));
							
							current = (hour * 60 * 60) + (min * 60) + sec;
							
							progress = (int)( ((float)current) / ((float)total) *100f );
						}
						
						if (newStatus != null)
						{
						 Message msg = mHandler.obtainMessage(1);
				         msg.getData().putInt("progress", progress);
				         msg.getData().putString("status", newStatus);
				         
				         mHandler.sendMessage(msg);
						}
					}
					
				};
				
				sendBroadcast(new Intent()
					.setAction(InformaConstants.Keys.Service.SET_CURRENT)
					.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, System.currentTimeMillis())
					.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.MEDIA_SAVED));
				
				// Could make some high/low quality presets	
				vc.processVideo(redactSettingsFile, obscureRegions, recordingFile, saveFile, format, mp.getVideoWidth(), mp.getVideoHeight(), frameRate, bitRate, sizeMult, sc);
				
			}
			catch (Exception e)
			{
				Log.e(LOG,"error with ffmpeg",e);
			}
			
			wl.release();
		     
			if (!mCancelled)
			{
				addVideoToGallery(saveFile);
				
				Message msg = mHandler.obtainMessage(completeActionFlag);
				msg.getData().putString("status","complete");
				mHandler.sendMessage(msg);
			}
	         
		}
		
		
	};
	
	@Override	
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock_Light);
		super.onCreate(savedInstanceState);
		
		setLayout();
		
		videoWidth = videoHeight = 0;
		isPlaying = mCancelled = mAutoDetectEnabled = false;
		
		originalVideoUri = getIntent().getData();
		
		if (originalVideoUri == null) {
			if (getIntent().hasExtra(Intent.EXTRA_STREAM)) {
				originalVideoUri = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
			}
		}
			
		if (originalVideoUri == null) {
			finish();
			return;
		}
		
		recordingFile = new File(originalVideoUri.getPath());
		firstTimestamp = getIntent().getLongExtra(InformaConstants.Keys.CaptureEvent.MEDIA_CAPTURE_COMPLETE, 0L);
		sendBroadcast(new Intent()
			.setAction(InformaConstants.Keys.Service.SET_CURRENT)
			.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, firstTimestamp)
			.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.MEDIA_CAPTURED));
		
		mp = new MediaPlayer();
		mp.setOnErrorListener(this);
		mp.setOnCompletionListener(this);
		mp.setOnPreparedListener(this);
		mp.setOnBufferingUpdateListener(this);
		mp.setOnVideoSizeChangedListener(this);
		mp.setOnSeekCompleteListener(this);
		mp.setOnInfoListener(this);
		mp.setScreenOnWhilePlaying(true);
		
		t = new Timer();
		
		redactSettingsFile = new File(fileExternDir, "redact_unsort.txt");
		
		obscuredPaint = new Paint();
		obscuredPaint.setColor(Color.WHITE);
		obscuredPaint.setStyle(Style.STROKE);
		obscuredPaint.setStrokeWidth(10f);
		
		selectedPaint = new Paint();
		selectedPaint.setColor(Color.GREEN);
		selectedPaint.setStyle(Style.STROKE);
		selectedPaint.setStrokeWidth(10f);
		
		bitmapCornerUL = BitmapFactory.decodeResource(getResources(), R.drawable.edit_region_corner_ul);
		bitmapCornerUR = BitmapFactory.decodeResource(getResources(), R.drawable.edit_region_corner_ur);
		bitmapCornerLL = BitmapFactory.decodeResource(getResources(), R.drawable.edit_region_corner_ll);
		bitmapCornerLR = BitmapFactory.decodeResource(getResources(), R.drawable.edit_region_corner_lr);
		
		vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
		mAutoDetectEnabled = true;
		
	}
	
	private void setLayout() {
		ab = getSupportActionBar();
    	ab.setDisplayShowHomeEnabled(false);
    	ab.setDisplayShowTitleEnabled(false);
    	
    	setContentView(R.layout.video_editor);
    	
    	playPause = (ImageButton) findViewById(R.id.editor_playPause);
    	playPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.editor_play));
		playPause.setOnClickListener(this);
		
		sv = (SurfaceView) findViewById(R.id.editor_surfaceView);
		holder = sv.getHolder();
		holder.addCallback(this);
		
		seekBar = (SeekBar) findViewById(R.id.editor_seekBar);
		seekBar.setThumbOffset(0);
		seekBar.setOnSeekBarChangeListener(this);
		
		breakpointHolder = (RelativeLayout) findViewById(R.id.editor_breakpointHolder);
		
		regionsView = (ImageView) findViewById(R.id.editor_regionsView);
		regionsView.setOnTouchListener(this);
		
		createCleanSavePath();
    	
	}
	
	private void setSeekBar() {
		seekBar.setMax(mDuration);
		seekBar.setProgress(0);
		seekBar.setOnTouchListener(this);
		
		tt = new TimerTask() {
			@Override
			public void run() {
				h.post(new Runnable() {
					@Override
					public void run() {
						if(isPlaying && seekBar.getProgress() < mDuration) {
							seekBar.setProgress(mp.getCurrentPosition());
							updateRegionDisplay();
						}
					}
				});
			}
		};
		togglePlayButton();
		t.schedule(tt, 0, 250);
	}
	
	private void setDimensions() {
		screen = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		screen.getSize(size);
		Log.d(LOG, "screen size: " + size.x + "," + size.y);
		
		videoWidth = mp.getVideoWidth();
		videoHeight = mp.getVideoHeight();
		
		vRatio = ((float) size.x) / ((float) videoWidth);
		Log.v(LOG, "video/screen ratio: " + vRatio);
		
		LayoutParams lp = sv.getLayoutParams();
		lp.width = size.x;
		lp.height = size.y;
		
		sv.setLayoutParams(lp);
		regionsView.setLayoutParams(lp);
		
		Rect cast = new Rect();
		seekBar.getLocalVisibleRect(cast);
		
		breakpointHeight = cast.height();
		breakpointWidth = cast.width() - 5;
		
		playPause.getLocalVisibleRect(cast);
		breakpointLeft = size.x - cast.right;
		breakpointTop = cast.top;
		breakpointOffset = size.x - breakpointLeft;
		
		Log.d(LOG, "offset: " + breakpointOffset + "\ndims: " + breakpointTop + ", " + breakpointLeft + ", " + breakpointWidth + ", " + breakpointHeight);
	}
	
	private void togglePlayButton() {
		if(isPlaying) {
			playPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.editor_play));
			mp.pause();
			breakpointHolder.setVisibility(View.VISIBLE);
			isPlaying = false;
			mAutoDetectEnabled = false;
		} else {
			playPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.editor_pause));
			mp.start();
			breakpointHolder.setVisibility(View.GONE);
			isPlaying = true;
		}
	}
	
	private void updateRegionDisplay() {

		Log.d(LOG, "updating region display!");
		
		validateRegionView();
		clearRects();
				
		for (ObscureRegion region : obscureRegions) {
			if (region.existsInTime(mp.getCurrentPosition())) {
				// Draw this region
				Log.v(LOG,mp.getCurrentPosition() + " Drawing a region: " + region.getBounds().left + " " + region.getBounds().top + " " + region.getBounds().right + " " + region.getBounds().bottom);
				if (region != activeRegion && region != regionInContext) {
					displayRegion(region,false);
				}
			}
		}
		
		if (activeRegion != null && activeRegion.existsInTime(mp.getCurrentPosition())) {
			regionInContext = activeRegion;
			//displayRect(activeRegion.getBounds(), selectedPaint);
		}
		
		if(regionInContext != null) {
			if(!mp.isPlaying()) {
				displayRegion(regionInContext,true);
				if(!menu.getItem(0).isVisible())
					menu.getItem(0).setVisible(true);
			} else {
				displayRegion(regionInContext, false);
				if(menu.getItem(0).isVisible())
					menu.getItem(0).setVisible(false);
			}
		} else {
			if(menu.getItem(0).isVisible())
				menu.getItem(0).setVisible(false);
		}
		
		regionsView.invalidate();
		seekBar.invalidate();
	}
	
	private void validateRegionView() {
		if (obscuredBmp == null && regionsView.getWidth() > 0 && regionsView.getHeight() > 0) {
			Log.v(LOG,"obscuredBmp is null, creating it now");
			obscuredBmp = Bitmap.createBitmap(regionsView.getWidth(), regionsView.getHeight(), Bitmap.Config.ARGB_8888);
			obscuredCanvas = new Canvas(obscuredBmp); 
		    regionsView.setImageBitmap(obscuredBmp);			
		}
	}
	
	private void displayRegion(ObscureRegion region, boolean selected) {

		RectF paintingRect = new RectF();
    	paintingRect.set(region.getBounds());    	
    	paintingRect.left *= vRatio;
    	paintingRect.right *= vRatio;
    	paintingRect.top *= vRatio;
    	paintingRect.bottom *= vRatio;
    	
    
    	if (selected) {
	
        	paintingRect.inset(10,10);
        	
        	obscuredPaint.setStrokeWidth(5f);
    		obscuredPaint.setColor(Color.GREEN);
        	
    		obscuredCanvas.drawRect(paintingRect, obscuredPaint);
    		
        	obscuredCanvas.drawBitmap(bitmapCornerUL, paintingRect.left - VideoRegion.REGION_CORNER_SIZE, paintingRect.top - VideoRegion.REGION_CORNER_SIZE, obscuredPaint);
    		obscuredCanvas.drawBitmap(bitmapCornerLL, paintingRect.left - VideoRegion.REGION_CORNER_SIZE, paintingRect.bottom - (VideoRegion.REGION_CORNER_SIZE/2), obscuredPaint);
    		obscuredCanvas.drawBitmap(bitmapCornerUR, paintingRect.right - (VideoRegion.REGION_CORNER_SIZE/2), paintingRect.top - VideoRegion.REGION_CORNER_SIZE, obscuredPaint);
    		obscuredCanvas.drawBitmap(bitmapCornerLR, paintingRect.right - (VideoRegion.REGION_CORNER_SIZE/2), paintingRect.bottom - (VideoRegion.REGION_CORNER_SIZE/2), obscuredPaint);
    	    
    	} else {
    		obscuredPaint.setStrokeWidth(5f);
    		obscuredPaint.setColor(Color.WHITE);
    		obscuredCanvas.drawRect(paintingRect, obscuredPaint);    		
    	}
	}
	
	private void clearRects() {
		Paint clearPaint = new Paint();
		clearPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
		
		if (obscuredCanvas != null)
			obscuredCanvas.drawPaint(clearPaint);
	}
		
	private String pullPathFromUri(Uri uri) {
		String originalVideoFilePath = null;
    	String[] columnsToSelect = { MediaStore.Video.Media.DATA };
    	Cursor videoCursor = getContentResolver().query(uri, columnsToSelect, null, null, null );
    	if ( videoCursor != null && videoCursor.getCount() == 1 ) {
	        videoCursor.moveToFirst();
	        originalVideoFilePath = videoCursor.getString(videoCursor.getColumnIndex(MediaStore.Images.Media.DATA));
    	}

    	return originalVideoFilePath;
	}
	
	private void createCleanSavePath() {
		try {
			saveFile = File.createTempFile("output", ".mp4", fileExternDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int getRegionCornerMode(ObscureRegion region, float x, float y)
	{    			
    	if (Math.abs(region.getBounds().left-x) < VideoRegion.REGION_CORNER_SIZE
    			&& Math.abs(region.getBounds().top-y) < VideoRegion.REGION_CORNER_SIZE)
    	{
    		Log.v(LOG,"CORNER_UPPER_LEFT");
    		return VideoRegion.CORNER_UPPER_LEFT;
    	}
    	else if (Math.abs(region.getBounds().left-x) < VideoRegion.REGION_CORNER_SIZE
    			&& Math.abs(region.getBounds().bottom-y) < VideoRegion.REGION_CORNER_SIZE)
    	{
    		Log.v(LOG,"CORNER_LOWER_LEFT");
    		return VideoRegion.CORNER_LOWER_LEFT;
		}
    	else if (Math.abs(region.getBounds().right-x) < VideoRegion.REGION_CORNER_SIZE
    			&& Math.abs(region.getBounds().top-y) < VideoRegion.REGION_CORNER_SIZE)
    	{
        		Log.v(LOG,"CORNER_UPPER_RIGHT");
    			return VideoRegion.CORNER_UPPER_RIGHT;
		}
    	else if (Math.abs(region.getBounds().right-x) < VideoRegion.REGION_CORNER_SIZE
        			&& Math.abs(region.getBounds().bottom-y) < VideoRegion.REGION_CORNER_SIZE)
    	{
    		Log.v(LOG,"CORNER_LOWER_RIGHT");
    		return VideoRegion.CORNER_LOWER_RIGHT;
    	}
    	
		Log.v(LOG,"CORNER_NONE");    	
    	return VideoRegion.CORNER_NONE;
	}
	
	public ObscureRegion findRegion(float x, float y) {
		ObscureRegion returnRegion = null;
		
		for (ObscureRegion region : obscureRegions)
		{
			if (region.getBounds().contains(x,y))
			{
				returnRegion = region;
				break;
			}
		}			
		return returnRegion;
	}
	
	private void createNewRegion(ObscureRegion or) {
		vibe.vibrate(100);
		
		activeRegion = or;
		obscureRegions.add(activeRegion);
		
		regionInContext = activeRegion;
		
		if(!menu.getItem(0).isVisible())
			menu.getItem(0).setVisible(true);
		
		Log.d(LOG,"Creating a new activeRegion");
	}
	
	private void createNewRegion() {
		createNewRegion(new ObscureRegion(this, mDuration, mp.getCurrentPosition() - timeNudgeOffset, mDuration, posX, posY));
	}
	
	private void createNewRegion(long previousEndTime, ObscureRegion lastRegion) {
		createNewRegion(new ObscureRegion(this, mDuration, mp.getCurrentPosition(), previousEndTime, lastRegion.sx, lastRegion.sy, posX, posY, ObscureRegion.DEFAULT_MODE));
	}
	
	private void createNewRegion(long previousEndTime) {
		createNewRegion(new ObscureRegion(this, mDuration, mp.getCurrentPosition(), previousEndTime, posX, posY));
	}
	
	// TODO
	public void launchTagger(ObscureRegion or) {
    	Intent informa = new Intent(this, Tagger.class);
    	//informa.putExtra(ObscuraConstants.ImageRegion.PROPERTIES, ir.getRegionProcessor().getProperties());
    	//informa.putExtra(InformaConstants.Keys.ImageRegion.INDEX, obscureRegions.indexOf(ir));
    	
    	//ir.getRegionProcessor().processRegion(new RectF(ir.getBounds()), obscuredCanvas, obscuredBmp);
    	
    	/*
    	if(ir.getRegionProcessor().getBitmap() != null) {
    		Bitmap b = ir.getRegionProcessor().getBitmap();
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		b.compress(Bitmap.CompressFormat.JPEG, 50, baos);
    		informa.putExtra(InformaConstants.Keys.ImageRegion.THUMBNAIL, baos.toByteArray());
    	}
    	*/
    	
    	//startActivityForResult(informa, InformaConstants.FROM_INFORMA_TAGGER);
    	
    }
	
	private void processVideo() {
    	
    	mCancelled = false;
    	
    	mp.pause();
    	//mediaPlayer.release();
    	
    	progressDialog = new ProgressDialog(this);
    	progressDialog.setMessage("Processing. Please wait...");
    	progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	progressDialog.setMax(100);
        progressDialog.setCancelable(true);
       
    	 Message msg = mHandler.obtainMessage(2);
         msg.getData().putString("status","cancelled");
         progressDialog.setCancelMessage(msg);
    	
         progressDialog.show();
     	
		// Convert to video
		Thread thread = new Thread (runProcessVideo);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
    }
    
	private void addVideoToGallery (File videoToAdd)
	{
		/*
		   // Save the name and description of a video in a ContentValues map.  
        ContentValues values = new ContentValues(2);
        values.put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_MP4);
        // values.put(MediaStore.Video.Media.DATA, f.getAbsolutePath()); 

        // Add a new record (identified by uri) without the video, but with the values just set.
        Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        // Now get a handle to the file for that record, and save the data into it.
        try {
            InputStream is = new FileInputStream(videoToAdd);
            OutputStream os = getContentResolver().openOutputStream(uri);
            byte[] buffer = new byte[4096]; // tweaking this number may increase performance
            int len;
            while ((len = is.read(buffer)) != -1){
                os.write(buffer, 0, len);
            }
            os.flush();
            is.close();
            os.close();
        } catch (Exception e) {
            Log.e(LOGTAG, "exception while writing video: ", e);
        } 
        */
		
	
     // force mediascanner to update file
     		MediaScannerConnection.scanFile(
     				this,
     				new String[] {videoToAdd.getAbsolutePath()},
     				new String[] {ObscuraConstants.MIME_TYPE_MP4},
     				null);

//        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
     		
     		/* TODO: seal log!
        	Intent informa = new Intent()
    			.setAction(InformaConstants.Keys.Service.SEAL_LOG)
    			.putExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.VIDEO)
    			.putExtra(InformaConstants.Keys.ImageRegion.DATA, imageRegionObject.toString())
    			.putExtra(InformaConstants.Keys.Image.LOCAL_MEDIA_PATH, pullPathFromUri(savedImageUri).getAbsolutePath());
    			
    		if(encryptList[0] != 0)
    			informa.putExtra(InformaConstants.Keys.Intent.ENCRYPT_LIST, encryptList);
    		
        	sendBroadcast(informa);
        	*/
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater mi = getSupportMenuInflater();
		mi.inflate(R.menu.video_editor_menu, menu);
		
		menu.getItem(0).setVisible(false);
		
		this.menu = menu;
    	
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {	
        switch (item.getItemId()) {
        case R.id.menu_current_region_set_inpoint:
        	regionInContext.setProperty(ObscuraConstants.VideoEditor.Breakpoints.IN, mp.getCurrentPosition());
        	regionInContext.breakpoint.redraw();
        	updateRegionDisplay();
        	return true;
        case R.id.menu_current_region_set_outpoint:
        	regionInContext.setProperty(ObscuraConstants.VideoEditor.Breakpoints.OUT, mp.getCurrentPosition());
        	regionInContext.breakpoint.redraw();
        	updateRegionDisplay();
        	return true;
        case R.id.menu_current_region_pixelate:
        	regionInContext.setProperty(ObscuraConstants.VideoEditor.Breakpoints.FILTER, ObscuraConstants.Filters.PIXELIZE);
        	return true;
        case R.id.menu_current_region_identify:
        	regionInContext.setProperty(ObscuraConstants.VideoEditor.Breakpoints.FILTER, ObscuraConstants.Filters.INFORMA_TAGGER);
        	return true;
        case R.id.menu_current_region_delete:
        	if(regionInContext != null) {
        		breakpointHolder.removeView(regionInContext.breakpoint);
        		obscureRegions.remove(regionInContext);
        		regionInContext = null;
        		updateRegionDisplay();
        	}
        	return true;
        case R.id.menu_detect:
        	return true;
        case R.id.menu_clear_regions:
        	for(ObscureRegion or : obscureRegions) {
        		breakpointHolder.removeView(or.breakpoint);
        	}
        	obscureRegions.clear();
        	regionInContext = null;
        	updateRegionDisplay();
        	return true;
        case R.id.menu_save:
        	completeActionFlag = 3;
    		processVideo();
        	return true;
        default:
        	return false;
        }
	}
	
	@Override
	public void onBufferingUpdate(MediaPlayer mp, int b) {}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mDuration = mp.getDuration();
		setDimensions();
		setSeekBar();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		togglePlayButton();	
	}

	@Override
	public boolean onError(MediaPlayer mp, int error, int extras) {
		return false;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if(fromUser) {
			mp.seekTo(progress);
			updateRegionDisplay();
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			mp.setDataSource(getApplicationContext(), originalVideoUri);
			mp.setDisplay(holder);
			mp.prepare();
		} catch (IllegalArgumentException e) {
			Log.d(LOG, e.toString());
			finish();
		} catch (SecurityException e) {
			Log.d(LOG, e.toString());
			finish();
		} catch (IllegalStateException e) {
			Log.d(LOG, e.toString());
			finish();
		} catch (IOException e) {
			Log.d(LOG, e.toString());
			finish();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		boolean handled = false;

		if (v == seekBar) {
			// It's the progress bar/scrubber
			if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
			    mp.start();
		    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
		    	mp.pause();
		    }
			
			mp.seekTo((int)(mp.getDuration()*(float)(event.getX()/seekBar.getWidth())));
			updateRegionDisplay();
			// Attempt to get the player to update it's view - NOT WORKING
			
			handled = false; // The progress bar doesn't get it if we have true here
		} else {
			// Region Related
			//float x = event.getX()/(float)currentDisplay.getWidth() * videoWidth;
			//float y = event.getY()/(float)currentDisplay.getHeight() * videoHeight;
			posX = event.getX() / vRatio;
			posY = event.getY() / vRatio;
			
			Log.d(LOG, "touch event:\nx: " + posX + "y: " + posY + "\nvRatio: " + vRatio);

			switch (event.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:

					// Single Finger down
					currentNumFingers = 1;
					
					// If we have a region in creation/editing and we touch within it
					if (activeRegion != null && activeRegion.getRectF().contains(posX, posY)) {

						// Should display menu, unless they move
						//showMenu = true;
						
						// Are we on a corner?
						regionCornerMode = getRegionCornerMode(activeRegion, posX, posY);
						
						Log.v(LOG,"Touched activeRegion");
																		
					} else {
					
						//showMenu = false;
						
						ObscureRegion previouslyActiveRegion = activeRegion;
						
						activeRegion = findRegion(posX, posY);
						
						if (activeRegion != null)
						{
							if (previouslyActiveRegion == activeRegion)
							{
								// Display menu unless they move
								//showMenu = true;
								
								// Are we on a corner?
								regionCornerMode = getRegionCornerMode(activeRegion, posX, posY);
								
								// TODO: Show in and out points
								//progressBar.setThumbsActive((int)(activeRegion.startTime/mDuration*100), (int)(activeRegion.endTime/mDuration*100));

								// They are interacting with the active region
								Log.d(LOG,"Touched an active region");
							}
							else
							{
								// They are interacting with the active region
								Log.d(LOG,"Touched an existing region, make it active");
							}
							regionInContext = activeRegion;
							// TODO: show menu item
							
						} else {
							createNewRegion();
						}
						handled = true;
					}
					break;
				case MotionEvent.ACTION_UP:
					// Single Finger Up
					currentNumFingers = 0;
					
										
					if (activeRegion != null) {
						if (mp.isPlaying())
							activeRegion.endTime = mp.getCurrentPosition();
						else
							activeRegion.endTime = mDuration;
						
						activeRegion = null;
					} else {
						if(menu.getItem(0).isVisible())
							menu.getItem(0).setVisible(false);
						regionInContext = null;
					}
					
					break;
										
				case MotionEvent.ACTION_MOVE:
					// Calculate distance moved
					
					if (activeRegion != null && mp.getCurrentPosition() > activeRegion.startTime) {
						Log.v(LOG,"Moving a activeRegion");
						
						long previousEndTime = activeRegion.endTime;
						activeRegion.endTime = mp.getCurrentPosition();
						
						ObscureRegion lastRegion = activeRegion;
						activeRegion = null;
						
						if (regionCornerMode != VideoRegion.CORNER_NONE) {
				
							//moveRegion(float _sx, float _sy, float _ex, float _ey)
							// Create new region with moved coordinates
							if (regionCornerMode == VideoRegion.CORNER_UPPER_LEFT) {
								createNewRegion(previousEndTime, lastRegion);
								
							} else if (regionCornerMode == VideoRegion.CORNER_LOWER_LEFT) {
								createNewRegion(previousEndTime, lastRegion);
							} else if (regionCornerMode == VideoRegion.CORNER_UPPER_RIGHT) {
								createNewRegion(previousEndTime, lastRegion);
							} else if (regionCornerMode == VideoRegion.CORNER_LOWER_RIGHT) {
								createNewRegion(previousEndTime, lastRegion);
							}
						} else {		
							// No Corner
							createNewRegion(previousEndTime);
							obscureRegions.add(activeRegion);
						}
						
						if (activeRegion != null) {
							// TODO: Show in and out points
							//progressBar.setThumbsActive((int)(activeRegion.startTime/mDuration*100), (int)(activeRegion.endTime/mDuration*100));
						}
						
					} else if (activeRegion != null) {
						Log.v(LOG,"Moving activeRegion start time");
						
						if (regionCornerMode != VideoRegion.CORNER_NONE) {
							
							// Just move region, we are at begin time
							if (regionCornerMode == VideoRegion.CORNER_UPPER_LEFT) {
								activeRegion.moveRegion(posX, posY, activeRegion.ex, activeRegion.ey);
							} else if (regionCornerMode == VideoRegion.CORNER_LOWER_LEFT) {
								activeRegion.moveRegion(posX, activeRegion.sy, activeRegion.ex, posY);
							} else if (regionCornerMode == VideoRegion.CORNER_UPPER_RIGHT) {
								activeRegion.moveRegion(activeRegion.sx, posY, posX, activeRegion.ey);
							} else if (regionCornerMode == VideoRegion.CORNER_LOWER_RIGHT) {
								activeRegion.moveRegion(activeRegion.sx, activeRegion.sy, posX, posY);
							}
						} else {		
							// No Corner
							activeRegion.moveRegion(posX, posY);
						}
						
						// TODO: Show in and out points
						//progressBar.setThumbsActive((int)(activeRegion.startTime/mDuration*100), (int)(activeRegion.endTime/mDuration*100));

					}
					
					handled = true;
					break;
			}
		}
		
		updateRegionDisplay();
		
		return handled; // indicate event was handled	
	}

	@Override
	public void onClick(View v) {
		if(v == playPause) {
			togglePlayButton();
		} else if(v instanceof ObscureRegion.Breakpoint) {
			regionInContext = ((ObscureRegion.Breakpoint) v).getRegion();
			updateRegionDisplay();
		}
		
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		Log.v(LOG, "onVideoSizeChanged Called");

		videoWidth = mp.getVideoWidth();
		videoHeight = mp.getVideoHeight();
		
		setDimensions();
		
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		return false;
	}

}
