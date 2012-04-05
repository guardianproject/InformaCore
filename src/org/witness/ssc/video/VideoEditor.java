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
import org.witness.informa.utils.VideoConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.io.ShellUtils;
import org.witness.informa.utils.io.ShellUtils.ShellCallback;
import org.witness.ssc.R;
import org.witness.ssc.image.detect.GoogleFaceDetection;
import org.witness.ssc.utils.ObscuraConstants;
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
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
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
	int rTop, rLeft, rWidth, rHeight, rOffset;
	
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
	
	ImageView regionsView;
	RelativeLayout breakpointHolder;
	
	public static final String LOG = ObscuraConstants.TAG;
	
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
				//launchInform();
				break;
			case 4:
				break;
			case 5:
				//updateRegionDisplay();
				break;
			default:
				super.handleMessage(msg);
				
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
		
		//mAutoDetectEnabled = true;
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
		
		LayoutParams lp = sv.getLayoutParams();
		lp.width = size.x;
		lp.height = size.y;
		
		sv.setLayoutParams(lp);
		regionsView.setLayoutParams(lp);
		
		Rect cast = new Rect();
		seekBar.getLocalVisibleRect(cast);
		
		rHeight = cast.height();
		rWidth = cast.width() - 5;
		
		playPause.getLocalVisibleRect(cast);
		rLeft = size.x - cast.right;
		rTop = cast.top;
		rOffset = size.x - rLeft;
		
	}
	
	private void togglePlayButton() {
		if(isPlaying) {
			playPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.editor_play));
			mp.pause();
			isPlaying = false;
		} else {
			playPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.editor_pause));
			mp.start();
			isPlaying = true;
		}
	}
	
	private boolean updateVideoLayout() {
		return false;
		
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater mi = getSupportMenuInflater();
		mi.inflate(R.menu.video_editor_menu, menu);
		
		//TODO: menu.getItem(0).setVisible(false);
		
		this.menu = menu;
    	
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {	
        switch (item.getItemId()) {
        case R.id.menu_current_region_pixelate:
        	return true;
        case R.id.menu_save:
        	return true;
        default:
        	return false;
        }
	}
	
	@Override
	public void onBufferingUpdate(MediaPlayer mp, int b) {
		// TODO Auto-generated method stub
		
	}

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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if(fromUser)
			mp.seekTo(progress);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onClick(View v) {
		if(v == playPause) {
			togglePlayButton();
		}
		
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		// TODO Auto-generated method stub
		return false;
	}

}
