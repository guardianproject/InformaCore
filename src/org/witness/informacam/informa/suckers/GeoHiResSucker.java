package org.witness.informacam.informa.suckers;

import java.util.List;
import java.util.TimerTask;

import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Logger;
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

public class GeoHiResSucker extends GeoSucker implements LocationListener {
	LocationManager lm;
	Criteria criteria;
	long currentNmeaTime = 0L;
	
	private final static String LOG = Suckers.LOG;
	
	@SuppressWarnings("unchecked")
	public GeoHiResSucker(Context context) {
		super(context);
		setSucker(this);
		
		lm = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
		
		if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
		} else {
			Log.d(LOG, "NETWORK PROVIDER is unavailable");
		}
		
		if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
		} else {
			Log.d(LOG, "GPS PROVIDER is unavailable");
		}
				
		lm.addNmeaListener(new NmeaListener() {

			@Override
			public void onNmeaReceived(long timestamp, String nmea) {
				//Log.d(Time.LOG, "but nmea says: timestamp: " + timestamp + "\n(" + nmea + ")");
				currentNmeaTime = timestamp;
			}
			
		});
		
		criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_MEDIUM);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		
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
		return new ILogPack(Geo.Keys.GPS_COORDS, "[" + loc[0] + "," + loc[1] + "]");
	}
	
	public long getTime() {
		return currentNmeaTime;
	}
	
	public double[] updateLocation() {
		Location l = null;
		double[] location = new double[] {0.0, 0.0};
		double[] isNull = new double[] {0.0, 0.0};

		try {
			List<String> providers = lm.getProviders(criteria, true);

			for(String provider : providers) {
				Logger.d(LOG, String.format("querying location provider %s", provider));
				l = lm.getLastKnownLocation(provider);

				if(l == null) {
					Logger.d(LOG, String.format("Location at provider %s is returning null...", provider));
					continue;
				}

				location = new double[] {l.getLatitude(), l.getLongitude()};
				Logger.d(LOG, String.format("new location: %f, %f", location[0], location[1]));

				if(location == isNull) {
					continue;
				} else {
					break;
				}

				
			}
		} catch(NullPointerException e) {
			Logger.e(LOG, e);
		} catch(IllegalArgumentException e) {
			Logger.e(LOG, e);
		}
		
		if(location == isNull) {
			return null;
		}
		
		return location;
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		lm.removeUpdates(this);
		//Log.d(LOG, "shutting down GeoSucker...");
	}

	@Override
	public void onLocationChanged(Location location) {
		currentNmeaTime = location.getTime();
	}

	@Override
	public void onProviderDisabled(String provider) {}

	@Override
	public void onProviderEnabled(String provider) {}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}	
}
