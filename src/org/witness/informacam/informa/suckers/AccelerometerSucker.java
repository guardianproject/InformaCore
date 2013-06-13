package org.witness.informacam.informa.suckers;

import java.util.List;
import java.util.TimerTask;

import org.json.JSONException;
import org.witness.informacam.informa.SensorLogger;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Accelerometer;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

@SuppressWarnings("rawtypes")
public class AccelerometerSucker extends SensorLogger implements SensorEventListener {
	SensorManager sm;
	List<Sensor> availableSensors;
	boolean hasAccelerometer, hasOrientation, hasLight, hasMagneticField;
	org.witness.informacam.models.j3m.ILogPack currentAccelerometer, currentLight, currentMagField;
	
	private final static String LOG = Suckers.LOG;
			
	@SuppressWarnings("unchecked")
	public AccelerometerSucker(Context context) {
		super(context);
		setSucker(this);
		
		sm = (SensorManager)context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
		availableSensors = sm.getSensorList(Sensor.TYPE_ALL);
		
		for(Sensor s : availableSensors) {
			switch(s.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				hasAccelerometer = true;
				sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
				break;
			case Sensor.TYPE_LIGHT:
				sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
				hasLight = true;
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
				hasOrientation = true;
				break;
			}
				
		}
		
		setTask(new TimerTask() {
			
			@Override
			public void run() {
				try {
					if(hasAccelerometer)
						readAccelerometer();
					if(hasLight)
						readLight();
					if(hasOrientation)
						readOrientation();
				} catch(JSONException e){}
			}
		});
		
		getTimer().schedule(getTask(), 0, Accelerometer.LOG_RATE);
	}
	
	private void readAccelerometer() throws JSONException, NullPointerException {
		if(currentAccelerometer != null)
			sendToBuffer(currentAccelerometer);
	}
	
	private void readOrientation() throws JSONException, NullPointerException {
		if(currentMagField != null)
			sendToBuffer(currentMagField);
	}
	
	private void readLight() throws JSONException, NullPointerException {
		if(currentLight != null)
			sendToBuffer(currentLight);
	}
	
	public ILogPack forceReturn() throws JSONException {
		ILogPack fr = new ILogPack(Accelerometer.Keys.ACC, currentAccelerometer);
		fr.put(Accelerometer.Keys.ORIENTATION, currentMagField);
		fr.put(Accelerometer.Keys.LIGHT, currentLight);
		return fr;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	@Override
	public void onSensorChanged(SensorEvent event) {
		synchronized(this) {
			if(getIsRunning()) {
				ILogPack sVals = new ILogPack();
				
				try {				
					switch(event.sensor.getType()) {
					case Sensor.TYPE_ACCELEROMETER:
						float[] acc = event.values.clone();
						sVals.put(Accelerometer.Keys.X, acc[0]);
						sVals.put(Accelerometer.Keys.Y, acc[1]);
						sVals.put(Accelerometer.Keys.Z, acc[2]);
						currentAccelerometer = sVals;
						break;
					case Sensor.TYPE_MAGNETIC_FIELD:
						float[] geoMag = event.values.clone();
						sVals.put(Accelerometer.Keys.AZIMUTH, geoMag[0]);
						sVals.put(Accelerometer.Keys.PITCH, geoMag[1]);
						sVals.put(Accelerometer.Keys.ROLL, geoMag[2]);
						currentMagField = sVals;
						break;
					case Sensor.TYPE_LIGHT:
						sVals.put(Accelerometer.Keys.LIGHT_METER_VALUE, event.values[0]);
						currentLight = sVals;
						break;
					}
					
					//sendToBuffer(sVals);
				} catch(JSONException e) {}
			}
		}
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		sm.unregisterListener(this);
		Log.d(LOG, "shutting down AccelerometerSucker...");
	}
}