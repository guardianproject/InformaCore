package org.witness.informacam.informa.suckers;

import java.text.DecimalFormat;
import java.util.List;
import java.util.TimerTask;

import org.json.JSONException;
import org.witness.informacam.informa.SensorLogger;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Accelerometer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Surface;

@SuppressWarnings("rawtypes")
public class AccelerometerSucker extends SensorLogger implements SensorEventListener {
	SensorManager sm;
	List<Sensor> availableSensors;
	boolean hasAccelerometer, hasOrientation, hasMagneticField;
	org.witness.informacam.models.j3m.ILogPack currentAccelerometer, currentMagField;

	private static int sScreenRotation;

     private DecimalFormat df = new DecimalFormat();

	private final static String LOG = Suckers.LOG;
			
	@SuppressWarnings("unchecked")
	public AccelerometerSucker(Context context) {
		super(context);
		setSucker(this);
		
        sScreenRotation = 0;

        df.setMaximumFractionDigits(1);
        df.setPositivePrefix("+");
		
		sm = (SensorManager)context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
		availableSensors = sm.getSensorList(Sensor.TYPE_ALL);
		
		for(Sensor s : availableSensors) {
			switch(s.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				hasAccelerometer = true;
				sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
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
		{
			if(currentAccelerometer != null)
			{
			//	float orientation = computeRealOrientation();
			//	currentMagField.put(Accelerometer.Keys.ORIENTATION, orientation+"");
			}
				
			sendToBuffer(currentMagField);
		
			
		}
	}
	
	
	public ILogPack forceReturn() throws JSONException {
		ILogPack fr = new ILogPack(Accelerometer.Keys.ACC, currentAccelerometer);
		fr.put(Accelerometer.Keys.ORIENTATION, currentMagField);
		return fr;
	}

	/*
	@Override
	public void onSensorChanged(SensorEvent event) {
		synchronized(this) {
			if(getIsRunning()) {
				ILogPack sVals = new ILogPack();
				
				try {				
					switch(event.sensor.getType()) {
					case Sensor.TYPE_ACCELEROMETER:
						mLastAcc = event.values.clone();
						sVals.put(Accelerometer.Keys.X, mLastAcc[0]);
						sVals.put(Accelerometer.Keys.Y, mLastAcc[1]);
						sVals.put(Accelerometer.Keys.Z, mLastAcc[2]);
						currentAccelerometer = sVals;
						break;
					case Sensor.TYPE_MAGNETIC_FIELD:
						mLastGeoMag = event.values.clone();
						sVals.put(Accelerometer.Keys.AZIMUTH, mLastGeoMag[0]);
						sVals.put(Accelerometer.Keys.PITCH, mLastGeoMag[1]);
						sVals.put(Accelerometer.Keys.ROLL, mLastGeoMag[2]);
						currentMagField = sVals;
						break;
					}
					
				} catch(JSONException e) {}
			}
		}
	}
	*/
	
public static void setScreenRotation(int screenRotation) {
        sScreenRotation = screenRotation;
         
 }

 private String frm(float sensorValue) {

         return df.format(sensorValue);
 }

 @Override
 public void onAccuracyChanged(Sensor sensor, int accuracy) {

      //   Log.v(TAG, "onAccuracyChanged() accuracy:" + accuracy);
 }

 
 // onSensorChanged cached values for performance, not all needed to be declared here.
 private float[] mGravity = new float[3];
 private float[] mGeomagnetic = new float[3];
 private boolean mGravityUsed, mGeomagneticUsed;

 private float azimuth; // View to draw a compass 2D represents North
 private float pitch, roll; // used to show id the device is horizontal
 private float inclination;// Magnetic north and real North
 private float alpha = 0.09f;// low pass filter factor
 private boolean useLowPassFilter = false; // set to true if you have a GUI implementation of compass!

 private float mOrientation[] ;//= new float[3];

 private int i = 0;
 private int iLimit = 1;
	
	/**
	 * taken from this xclnt project:
	 * https://github.com/matheszabi/PortaitLandscapeCompass/blob/master/src/com/example/compassfix/AccelerometerAndMagnetometerListener.java
	 */
	  @Override
      public void onSensorChanged(SensorEvent event) {

              if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                      mGravityUsed = false;
                      // apply a low pass filter: output = alpha*input + (1-alpha)*previous output;
                      if (!useLowPassFilter) {
                              mGravity[0] = alpha * event.values[0] + (1f - alpha) * mGravity[0];
                              mGravity[1] = alpha * event.values[1] + (1f - alpha) * mGravity[1];
                              mGravity[2] = alpha * event.values[2] + (1f - alpha) * mGravity[2];
                      } else {
                              mGravity = event.values.clone();
                      }
                     
                     try
                     {
	      				ILogPack sVals = new ILogPack();
	                    sVals.put(Accelerometer.Keys.X, mGravity[0]);
						sVals.put(Accelerometer.Keys.Y, mGravity[1]);
						sVals.put(Accelerometer.Keys.Z, mGravity[2]);
						currentAccelerometer = sVals;
                     }
                     catch (JSONException jse)
                     {
                    	 Log.d(LOG,"json exc",jse);
                     }
              }
              if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                      mGeomagneticUsed = false;
                      // apply a low pass filter: output = alpha*input + (1-alpha)*previous output;
                      if (useLowPassFilter) {
                              mGeomagnetic[0] = alpha * event.values[0] + (1f - alpha) * mGeomagnetic[0];
                              mGeomagnetic[1] = alpha * event.values[1] + (1f - alpha) * mGeomagnetic[1];
                              mGeomagnetic[2] = alpha * event.values[2] + (1f - alpha) * mGeomagnetic[2];
                      } else {
                              mGeomagnetic = event.values.clone();
                      }
                      
                      try
                      {
	        			  ILogPack sVals = new ILogPack();
	                      sVals.put(Accelerometer.Keys.AZIMUTH, mGeomagnetic[0]);
						  sVals.put(Accelerometer.Keys.PITCH, mGeomagnetic[1]);
						  sVals.put(Accelerometer.Keys.ROLL, mGeomagnetic[2]);
						  currentMagField = sVals;
                      }
                      catch (JSONException jse)
                      {
                     	 Log.d(LOG,"json exc",jse);
                      }

              }

              if (!mGravityUsed && !mGeomagneticUsed) {
                      float R[] = new float[9];
                      // X (product of Y and Z) and roughly points East
                      // Y: points to Magnetic NORTH and tangential to ground
                      // Z: points to SKY and perpendicular to ground
                      float I[] = new float[9];

                      // see axis_device.png
                      boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);

                      if (success) {
                              
                              // here need to use, but don't know which axes needed to be mapped:
                              //boolean remapped = SensorManager.remapCoordinateSystem(R, axisX, axisY, R);
                              // I will fix the result later

                              mOrientation = new float[3];
                              // see axis_globe_inverted.png
                              SensorManager.getOrientation(R, mOrientation);
                              inclination = SensorManager.getInclination(I);
                              
                              // not need to store values here, since they aren't fixed
//                              azimuth = orientation[0]; 
//                              pitch = orientation[1];  
//                              roll = orientation[2];   

                              // use values and wait for update for both values
                              mGravityUsed = true;
                              mGeomagneticUsed = true;        
                              
                              i++;
                      }
              }

              if (i == iLimit) {
                      i = 0;
                      
                 //     Log.d("CompassAngles", "Before Fix: azimuth: " + frm(mOrientation[0]) + ", pitch: " + frm(mOrientation[1]) + ", roll: " + frm(mOrientation[2]));
                      // expecting airplane rotation http://en.wikipedia.org/wiki/File:Rollpitchyawplain.png
                      switch (sScreenRotation) {
                      case Surface.ROTATION_0:
                              //Log.v("SurfaceRemap", "0 degree");
                              fixRotation0(mOrientation);
                              break;

                      case Surface.ROTATION_90:
                              //Log.v("SurfaceRemap", "90 degree");
                              fixRotation90(mOrientation);
                              break;

                      case Surface.ROTATION_180:
                              //Log.v("SurfaceRemap", "180 degree");
                              fixRotation180(mOrientation);
                              break;

                      case Surface.ROTATION_270:
                              //Log.v("SurfaceRemap", "270 degree");
                              fixRotation270(mOrientation);
                              break;

                      default:
                              Log.e("SurfaceRemap", "don't know the mScreenRotation value: " + sScreenRotation + " you should never seen this message!");
                              break;
                      }
                      
                      // expecting airplane rotation http://en.wikipedia.org/wiki/File:Rollpitchyawplain.png
               //       Log.d("CompassAngles", "After Fix: azimuth: " + frm(mOrientation[0]) + ", pitch: " + frm(mOrientation[1]) + ", roll: " + frm(mOrientation[2]));
                      
                      azimuth = mOrientation[0];
                      pitch = mOrientation[1];
                      roll = mOrientation[2];
                      
                      if (currentMagField != null)
                      {
	                      try
	                      {
		        			  
	                    	  currentMagField.put(Accelerometer.Keys.AZIMUTH_CORRECTED, mOrientation[0]);
	                    	  currentMagField.put(Accelerometer.Keys.PITCH_CORRECTED, mOrientation[1]);
	                    	  currentMagField.put(Accelerometer.Keys.ROLL_CORRECTED, mOrientation[2]);
	                      }
	                      catch (JSONException jse)
	                      {
	                     	 Log.d(LOG,"json exc",jse);
	                      }
                      }
                      
                      // separate sensor reference and maybe on new thread too is time consuming:                        
                      
              }
      }

      public static final void fixRotation0(float[] orientation) {//azimuth, pitch, roll
              orientation[1] = -orientation[1];// pitch = -pitch
      }

      public static final  void fixRotation90(float[] orientation) {//azimuth, pitch, roll
              orientation[0] += Math.PI/2f; // offset
              float tmpOldPitch = orientation[1];
              orientation[1] = -orientation[2]; //pitch = -roll
              orientation[2] = -tmpOldPitch;         // roll  = -pitch        
      }

      public static final  void fixRotation180(float[] orientation) {//azimuth, pitch, roll
              orientation[0] = (float) (orientation[0] > 0f ? (orientation[0] - Math.PI): (orientation[0] + Math.PI));// offset
              orientation[2] = -orientation[2];// roll = -roll
      }

      public static final  void fixRotation270(float[] orientation) {//azimuth, pitch, roll
              orientation[0] -= Math.PI/2;// offset
              float tmpOldPitch = orientation[1];
              orientation[1] = orientation[2]; //pitch = roll
              orientation[2] = tmpOldPitch;         // roll  = pitch        
      }

	
	
	public void stopUpdates() {
		setIsRunning(false);
		sm.unregisterListener(this);
		Log.d(LOG, "shutting down AccelerometerSucker...");
	}
}