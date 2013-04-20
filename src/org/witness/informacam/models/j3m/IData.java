package org.witness.informacam.models.j3m;

import java.util.List;

import org.witness.informacam.models.Model;

public class IData extends Model {
	public List<ISensorCapture> sensorCapture;
	public List<IRegionData> regionData;
	public IExif exif;
}
