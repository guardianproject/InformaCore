package org.witness.informacam.models.j3m;

import org.json.JSONObject;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;

public class ISensorCapture extends Model {
	public long timestamp = 0L;
	public int captureType = CaptureEvent.SENSOR_PLAYBACK;
	public JSONObject sensorPlayback = null;
	
	public ISensorCapture(long timestamp, JSONObject sensorPlayback) {
		this.timestamp = timestamp;
		this.sensorPlayback = sensorPlayback;
	}
}
