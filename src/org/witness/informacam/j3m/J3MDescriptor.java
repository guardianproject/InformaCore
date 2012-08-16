package org.witness.informacam.j3m;

import info.guardianproject.iocipher.File;

import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.UploaderService;

public class J3MDescriptor {
	UploaderService s;
	
	public String originalHash, versionLocation, serverUrl, clientPGPFingerprint;
	public int status, mediaType;
	public long totalBytesExpected, zippedBytesExpected, lastByteSent, timestampCreated;
	public byte[] zippedData;
	
	public J3MTorrent j3mTorrent;
	public File mediaFile;
	
	public interface J3MListener {
		public void onStatusChanged(J3MDescriptor j3m, int newStatus);
	}
	
	public J3MDescriptor(UploaderService s) {
		this.s = s;
		lastByteSent = 0;
	}
	
	public void setFile(String versionLocation) {
		this.versionLocation = versionLocation;
		mediaFile = IOCipherService.getInstance().getFile(versionLocation);
		totalBytesExpected = mediaFile.length();
		zippedData = IOUtility.zipFile(mediaFile);
		zippedBytesExpected = zippedData.length;
	}
	
	public void changeStatus(int newStatus) {
		((UploaderService) s).onStatusChanged(this, newStatus);
	}
	
	public void J3Mify() {
		j3mTorrent = new J3MTorrent();
	}
	
	public class J3MTorrent {
		public J3MTorrent() {
			// make a folder for the j3m torrent
			
			// 
		}
	}
}
