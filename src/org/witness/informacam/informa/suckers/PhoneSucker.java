package org.witness.informacam.informa.suckers;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.informa.SensorLogger;
import org.witness.informacam.utils.Constants.Suckers;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

@SuppressWarnings("rawtypes")
public class PhoneSucker extends SensorLogger {
	TelephonyManager tm;
	BluetoothAdapter ba;
	
	boolean hasBluetooth = false;
	
	@SuppressWarnings("unchecked")
	public PhoneSucker(InformaService is) {
		super(is);
		setSucker(this);
				
		tm = (TelephonyManager) is.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		ba = BluetoothAdapter.getDefaultAdapter();
		
		if(ba != null)
		{
			hasBluetooth = true;
			// if bluetooth is off, turn it on... (be sure to turn off when finished)
			if(!ba.isEnabled())
				ba.enable();
	
			
		
		}
		else
			Log.d(Suckers.LOG,"no bt?");
		
		// TODO: if bluetooth is off, turn it on... (be sure to turn off when finished)
		setTask(new TimerTask() {
			
			@Override
			public void run() throws NullPointerException {
				if(getIsRunning()) {
					try {
						sendToBuffer(new LogPack(Suckers.Phone.Keys.CELL_ID, getCellId()));
						
						// find other bluetooth devices around
						if(hasBluetooth && !ba.isDiscovering())
							ba.startDiscovery();
						
					} catch (JSONException e) {}
				}
			}
		});
		
		getTimer().schedule(getTask(), 0, Suckers.Phone.LOG_RATE);
	}
	
	public String getIMEI() {
		try {
			return tm.getDeviceId();
		} catch(NullPointerException e) {
			Log.e(Suckers.LOG,"getIMEI error",e);
			return null;
		}
	}
	
	public String getCellId() {	
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
	
	public List<String> getWifiNetworks() {
		List<String> wifi = new ArrayList<String>();
		
		return wifi;
	}
	
	public LogPack forceReturn() throws JSONException {
		LogPack fr = new LogPack(Suckers.Phone.Keys.IMEI, getIMEI());
		fr.put(Suckers.Phone.Keys.BLUETOOTH_DEVICE_ADDRESS, ba.getAddress());
		fr.put(Suckers.Phone.Keys.BLUETOOTH_DEVICE_NAME, ba.getName());
		fr.put(Suckers.Phone.Keys.CELL_ID, getCellId());
		
		return fr;
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		if(hasBluetooth && ba.isDiscovering()) {
			ba.cancelDiscovery();
			ba.disable();
		}
		
		Log.d(Suckers.LOG, "shutting down PhoneSucker...");
	}

}