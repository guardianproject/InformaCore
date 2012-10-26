package org.witness.informacam.informa;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;

public class SensorLogger<T> {
	public T _sucker;
	
	Timer mTimer = new Timer();
	TimerTask mTask;
	
	String tag;
	
	File mLog;
	JSONArray mBuffer;
	
	InformaService is;
	
	boolean isRunning;
		
	public interface OnUpdateListener {
		public void onUpdate(long timestamp, LogPack logPack);
	}
	
	public SensorLogger(InformaService is) {
		this.is = is;
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

	public void sendToBuffer(LogPack logPack) throws JSONException {
		logPack.put(CaptureEvent.Keys.TYPE, CaptureEvent.SENSOR_PLAYBACK);
		((OnUpdateListener) is).onUpdate(System.currentTimeMillis(), logPack); 
	}
}
