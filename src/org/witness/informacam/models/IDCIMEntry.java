package org.witness.informacam.models;

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
	public IExif exif;
	
	public long size;
	public long timeCaptured = 0L;
	public long id;
		
}