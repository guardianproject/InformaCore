package org.witness.informacam.informa.suckers;

import java.util.TimerTask;

import org.witness.informacam.informa.SensorLogger;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Geo;

import android.app.Activity;
import android.content.Context;
import android.location.Criteria;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

@SuppressWarnings("rawtypes")
public abstract class GeoSucker extends SensorLogger {
	
	
	public GeoSucker(Context context) {
		super(context);
	}

	public abstract ILogPack forceReturn();
	
	public abstract long getTime();
	
	public abstract double[] updateLocation();
	
	public abstract void stopUpdates();

}
