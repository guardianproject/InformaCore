package org.witness.informacam.utils.models;

public class IDCIMEntry extends Model {
	public String uri = null;
	public String fileName = null;
	public String name = null;
	public String hash = null;
	public String mediaType = null;
	public byte[] thumbnailFile = null;
	public String thumbnailName = null;
	public String thumbnailFileName = null;
	
	public long size;
	public long timeCaptured = 0L;
	public long id;
		
}