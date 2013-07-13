package org.witness.informacam.informa.suckers;

import java.util.TimerTask;

import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Suckers;
import org.witness.informacam.utils.Constants.Suckers.Geo;

import android.content.Context;
import android.location.Criteria;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class GeoLowResSucker extends GeoSucker implements LocationListener {
	LocationManager lm;
	Criteria criteria;
	long currentNmeaTime;
	
	private String mBestProvider = null;
	
	private final static String LOG = Suckers.LOG;
	
	private final static long MIN_TIME = 3000;
	private final static long MIN_DISTANCE = 1;
	
	@SuppressWarnings("unchecked")
	public GeoLowResSucker(Context context) {
		super(context);
		setSucker(this);
		
		lm = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
		
		
		/*
		if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
		} else {
			Log.d(LOG, "NETWORK PROVIDER is unavailable");
		}
		
		if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
		} else {
			Log.d(LOG, "GPS PROVIDER is unavailable");
		}*/
				
		lm.addNmeaListener(new NmeaListener() {

			@Override
			public void onNmeaReceived(long timestamp, String nmea) {
				//Log.d(LOG, "but nmea says: timestamp: " + timestamp + "\n(" + nmea + ")");
				currentNmeaTime = timestamp;
			}
			
		});
		
		criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_LOW);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		
		mBestProvider = lm.getBestProvider(criteria, true); //we only want providers that are on
		
		lm.requestLocationUpdates(mBestProvider, MIN_TIME, MIN_DISTANCE, this);
		
		setTask(new TimerTask() {

			@Override
			public void run() throws NullPointerException {
				if(getIsRunning()) {
					try {
						double[] loc = updateLocation();
						if (loc != null)
							sendToBuffer(new ILogPack(Geo.Keys.GPS_COORDS, "[" + loc[0] + "," + loc[1] + "]"));
					} catch(NullPointerException e) {
						Log.e(LOG, "location NPE", e);
					}
				}
			}
		});
		
		getTimer().schedule(getTask(), 0, Geo.LOG_RATE);
	}
	
	public ILogPack forceReturn() {
		double[] loc = updateLocation();
		if(loc == null) {
			Log.d(LOG, "location was null");
			loc = new double[] {0d, 0d};
		}
		
		return new ILogPack(Geo.Keys.GPS_COORDS, "[" + loc[0] + "," + loc[1] + "]");
	}
	
	public long getTime() {
		return currentNmeaTime;
	}
	
	public double[] updateLocation() {
		try {
			
			Location l = lm.getLastKnownLocation(mBestProvider);
			
			if (l != null) {
				//Log.d(LOG, "lat/lng: " + l.getLatitude() + ", " + l.getLongitude());
				return new double[] {l.getLatitude(),l.getLongitude()};
			} else {
				return null;
			}

			
		} catch(NullPointerException e) {
			Log.e(LOG,"location NPE", e);
			return null;
		} catch(IllegalArgumentException e) {
			Log.e(LOG, "location illegal arg",e);
			
			
			return null;
		}
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		lm.removeUpdates(this);
		//Log.d(LOG, "shutting down GeoSucker...");
	}

	@Override
	public void onLocationChanged(Location location) {}

	@Override
	public void onProviderDisabled(String provider) {}

	@Override
	public void onProviderEnabled(String provider) {}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}	
}
