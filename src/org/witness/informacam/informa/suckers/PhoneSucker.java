package org.witness.informacam.informa.suckers;

import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.informa.SensorLogger;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Suckers.Phone;
import org.witness.informacam.utils.Constants.Suckers;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

@SuppressWarnings("rawtypes")
public class PhoneSucker extends SensorLogger {
	TelephonyManager tm;
	BluetoothAdapter ba;
	WifiManager wm;
	
	boolean hasBluetooth = false;
	boolean hasWifi;
	boolean wifiWasOn = false;
	
	private final static String LOG = Suckers.LOG;
	
	@SuppressWarnings("unchecked")
	public PhoneSucker(Context context) {
		super (context);
		setSucker(this);
				
		tm = (TelephonyManager) context.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		ba = BluetoothAdapter.getDefaultAdapter();
		wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		
		if(ba != null)
		{
			hasBluetooth = true;
			// if bluetooth is off, turn it on... (be sure to turn off when finished)
			if(!ba.isEnabled())
				ba.enable();
		}
		else
			Log.d(LOG,"no bt?");
		
		if(wm != null) {
			// is wifi on?
			// if not, turn on, and set hasWifi to true
			if(wm.isWifiEnabled()) {
				hasWifi = true;
				wifiWasOn = true;
			} else {
				wm.setWifiEnabled(true);
			}
			
			
			
			// but don't let it auto-associate
		}
		
		// TODO: if bluetooth is off, turn it on... (be sure to turn off when finished)
		setTask(new TimerTask() {
			
			@Override
			public void run() {
				if(getIsRunning()) {
					try {
						sendToBuffer(new ILogPack(Phone.Keys.CELL_ID, getCellId()));
						
						// find other bluetooth devices around
						if(hasBluetooth && !ba.isDiscovering())
							ba.startDiscovery();
						
						// scan for network ssids
						if(!wm.startScan()) {
							// TODO: alert user to this error
							
						}
							
						
					} catch(NullPointerException e) {}
				}
			}
		});
		
		getTimer().schedule(getTask(), 0, Phone.LOG_RATE);
	}
	
	public String getIMEI() {
		try {
			return tm.getDeviceId();
		} catch(NullPointerException e) {
			Log.e(LOG,"getIMEI error",e);
			return null;
		}
	}
	
	private String getCellId() {	
		try {
			String out = "";
			if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
				final GsmCellLocation gLoc = (GsmCellLocation) tm.getCellLocation();
				out = Integer.toString(gLoc.getCid());
			} else if(tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
				final CdmaCellLocation cLoc = (CdmaCellLocation) tm.getCellLocation();
				out = Integer.toString(cLoc.getBaseStationId());
			}
			return out;
		} catch(NullPointerException e) {
			return null;
		}
	}
	
	public JSONArray getWifiNetworks() {
		JSONArray wifi = new JSONArray();
		for(ScanResult wc : wm.getScanResults()) {
			JSONObject scanResult = new JSONObject();
			try {
				scanResult.put(Phone.Keys.BSSID, wc.BSSID);
				scanResult.put(Phone.Keys.SSID, wc.SSID);
				wifi.put(scanResult);
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				continue;
			}
			
		}
		
		return wifi;
	}
	
	public ILogPack forceReturn() throws JSONException {
		ILogPack fr = new ILogPack(Phone.Keys.IMEI, getIMEI());
		if(ba != null) {
			fr.put(Phone.Keys.BLUETOOTH_DEVICE_ADDRESS, ba.getAddress());
			fr.put(Phone.Keys.BLUETOOTH_DEVICE_NAME, ba.getName());
		}
		
		String cId = getCellId() ;
		if(cId != null) {
			fr.put(Phone.Keys.CELL_ID, cId);
		}
		
		return fr;
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		if(hasBluetooth && ba.isDiscovering()) {
			ba.cancelDiscovery();
			ba.disable();
		}
		
		/*
		if(hasWifi && !wifiWasOn) {
			wm.setWifiEnabled(false);
		}
		*/
		
		
		
		Log.d(LOG, "shutting down PhoneSucker...");
	}

}