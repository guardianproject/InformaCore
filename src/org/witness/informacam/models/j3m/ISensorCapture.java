package org.witness.informacam.models.j3m;

import org.witness.informacam.utils.Constants.Suckers.CaptureEvent;
import org.witness.informacam.utils.LogPack;

public class ISensorCapture {
	public long timestamp = 0L;
	public int captureType = CaptureEvent.SENSOR_PLAYBACK;
	public LogPack sensorPlayback = null;
}
