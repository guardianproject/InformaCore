package org.witness.informacam.informa;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.SuckerCacheListener;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;
import org.witness.informacam.utils.LogPack;

import android.app.Activity;
import android.util.Log;

public class SensorLogger<T> {
	public T _sucker;
	
	Timer mTimer = new Timer();
	TimerTask mTask;
	
	String tag;
	
	File mLog;
	JSONArray mBuffer;
	
	protected Activity a;
	InformaCam informaCam;
		
	boolean isRunning;
	
	private final static String LOG = Suckers.LOG; 
		
	public SensorLogger() {
		informaCam = InformaCam.getInstance();
		a = informaCam.a;
		isRunning = true;
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
	
	public LogPack forceReturn() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, JSONException {
		if(_sucker.getClass().getDeclaredMethod("forceReturn", null) != null) {
			Method fr = _sucker.getClass().getDeclaredMethod("forceReturn", null);
			
			LogPack logPack = (LogPack) fr.invoke(_sucker, null);
			logPack.put(CaptureEvent.Keys.TYPE, CaptureEvent.SENSOR_PLAYBACK);
			
			return logPack;
		}
		
		return null;
	}

	public void sendToBuffer(final LogPack logPack) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logPack.put(CaptureEvent.Keys.TYPE, CaptureEvent.SENSOR_PLAYBACK);
					((SuckerCacheListener) informaCam.informaService).onUpdate(logPack);
				} catch(NullPointerException e) {}
				catch (JSONException e) {}
			}
		}).start();
		
	}
}
