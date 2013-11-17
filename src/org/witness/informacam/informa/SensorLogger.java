package org.witness.informacam.informa;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

public class SensorLogger<T> {
	public T _sucker;
	
	Timer mTimer = new Timer();
	TimerTask mTask;
	
	String tag;
	
	File mLog;
	JSONArray mBuffer;
	
	Context mContext;
	boolean isRunning;
	
	private final static String LOG = Suckers.LOG; 
		
	public SensorLogger(Context context) {
		isRunning = true;
		
		mContext = context;
	}
	
	public T getSucker() {
		return _sucker;
	}
	
	public void setSucker(T sucker) {
		_sucker = sucker;
	}
	
	public String getTag() {
		return tag;
	}
	
	public void setTag(String name) {
		this.tag = name;
	}
	
	public JSONArray getLog() {
		return mBuffer;
	}

	public Timer getTimer() {
		return mTimer;
	}
	
	public TimerTask getTask() {
		return mTask;
	}
	
	public void setTask(TimerTask task) {
		mTask = task;
	}
	
	public void setIsRunning(boolean b) {
		isRunning = b;
		if(!b)
			mTimer.cancel();
	}
	
	public boolean getIsRunning() {
		return isRunning;
	}
	
	public JSONObject returnFromLog() {
		JSONObject logged = new JSONObject();
		
		return logged;
	}
	
	public ILogPack forceReturn() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, JSONException {
		if(_sucker.getClass().getDeclaredMethod("forceReturn", null) != null) {
			Method fr = _sucker.getClass().getDeclaredMethod("forceReturn", null);
			
			ILogPack logPack = (ILogPack) fr.invoke(_sucker, null);
			if(logPack.captureTypes == null) {
				logPack.captureTypes = new ArrayList<Integer>();
			}
			logPack.captureTypes.add(CaptureEvent.SENSOR_PLAYBACK);
			
			return logPack;
		}
		
		return null;
	}

	public void sendToBuffer(final ILogPack logPack) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if(logPack.captureTypes == null) {
						logPack.captureTypes = new ArrayList<Integer>();
					}
					logPack.captureTypes.add(CaptureEvent.SENSOR_PLAYBACK);
					if (mSuckerCacheListener != null) {
						mSuckerCacheListener.onUpdate(logPack);
					}
					
					//((SuckerCacheListener) informaCam.informaService).onUpdate(logPack);
				} catch(NullPointerException e) {}
			}
		}).start();
		
	}
	
	private SuckerCacheListener mSuckerCacheListener;
	
	public void setSuckerCacheListener (SuckerCacheListener scl)
	{
		mSuckerCacheListener = scl;
	}
	
	public Context getContext ()
	{
		return mContext;
	}
}
