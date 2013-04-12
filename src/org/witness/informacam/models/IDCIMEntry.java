package org.witness.informacam.models;

import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.util.Log;

public class IDCIMEntry extends Model {
	public String uri = null;
	public String fileName = null;
	public String name = null;
	public String originalHash = null;
	public String bitmapHash = null;
	public String mediaType = null;
	public byte[] thumbnailFile = null;
	public String thumbnailName = null;
	public String thumbnailFileName = null;
	public String previewFrame = null;
	public IExif exif = null;

	public long size = 0L;
	public long timeCaptured = 0L;
	public long id = 0L;

	
	
	public boolean isAvailable() {
		int bytesAvailable = 0;
		do {
			bytesAvailable = InformaCam.getInstance().ioService.getBytes(fileName, Type.FILE_SYSTEM).length;
		} while(bytesAvailable <= 0);
		
		return true;
	}

}