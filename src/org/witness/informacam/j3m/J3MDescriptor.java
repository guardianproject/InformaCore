package org.witness.informacam.j3m;

import java.io.FileWriter;
import java.io.IOException;

import info.guardianproject.iocipher.File;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants.J3M;

import android.util.Base64;
import android.util.Log;

public class J3MDescriptor {
	UploaderService s;
	
	public String originalHash, versionLocation, serverUrl, clientPGPFingerprint, versionRoot;
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
		versionRoot = mediaFile.getPath();
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
		int mode, chunk_num, chunk_count = 0;
    	String id;
    	File torrent;
    	
    	StringBuffer sb;
    	
		public J3MTorrent() {
			// make a folder for the j3m torrent			
			torrent = new File(versionRoot + "torrent");
			if(!torrent.exists())
				torrent.mkdir();
		}
		
		public void build() {
			int offset = 0;
    		int numRead = 0;
    		
    		
		}
		
		public void setMode(int mode) {
			this.mode = mode;
			build();
		}
		
		private void addChunk() throws JSONException, IOException {
    		JSONObject chunk_description = new JSONObject();
    		chunk_description.put(J3M.Metadata.SOURCE, versionLocation);
    		chunk_description.put(J3M.Metadata.INDEX, chunk_count);
    		
    		int offset = chunk_count * mode;
    		
    		
    		byte[] fileBytes = new byte[(int) Math.min(mode, zippedData.length - offset)];
    		
    		Log.d(J3M.LOG, "total bytes: " + zippedData.length);
    		Log.d(J3M.LOG, "offset: " + offset);
    		Log.d(J3M.LOG, "byte length: " + fileBytes.length);
    		
    		// write offset + mode bytes to "blob" field as base 64 encoded string
    		System.arraycopy(zippedData, offset, fileBytes, 0, fileBytes.length);
    		chunk_description.put(J3M.Metadata.BLOB, Base64.encodeToString(fileBytes, Base64.DEFAULT));
    		chunk_description.put(J3M.Metadata.LENGTH, chunk_description.getString(J3M.Metadata.BLOB).length());
    		
    		// save output
    		FileWriter output = new FileWriter(new File(torrent + "/" + chunk_count + "_.j3mtorrent"));
    		output.write(chunk_description.toString());
    		output.flush();
    		output.close();
    		
    		sb.append("\nchunk #" + chunk_count + ": " + fileBytes.length);
    		
    		Log.d(J3M.LOG, chunk_description.toString());
    		chunk_count++;
    	}
	}
}
