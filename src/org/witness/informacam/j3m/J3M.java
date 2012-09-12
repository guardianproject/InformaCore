package org.witness.informacam.j3m;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.content.ContentValues;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

public class J3M {
	File file, root, j3mRoot;
	J3MDescriptor j3mdescriptor;
	byte[] fileBytes;
	static String LOG = Constants.J3M.LOG;
	int chunk_num, chunk_count = 0;
	int mode = Constants.J3M.Chunks.EXTRA_LARGE;
	int mediaType;
	long timestampCreated;
	
	IOCipherService ioCipherService = IOCipherService.getInstance();
	UploaderService uploaderService = UploaderService.getInstance();
	
	public J3M(String pgpKeyFingerprint, ContentValues cv, File file) {
		this.file = file;
		timestampCreated = cv.getAsLong(Media.Keys.TIME_CAPTURED);
		mediaType = cv.getAsInteger(Media.Keys.TYPE);
		
		int offset = 0;
		int numRead = 0;
		
		try {
			root = new File(Storage.FileIO.DUMP_FOLDER, MediaHasher.hash(file, "SHA-1"));
			if(!root.exists())
				root.mkdir();
						
			fileBytes = new byte[(int) file.length()];
			
			InputStream fis = new FileInputStream(file);
			while(offset < fileBytes.length && (numRead = fis.read(fileBytes, offset, fileBytes.length - offset)) > 0)
    			offset += numRead;
    		fis.close();
    		
    		chunk_num = (int) Math.ceil(fileBytes.length/mode) + 1;
    		
    		j3mRoot = new File(root, Constants.J3M.DUMP_FOLDER);
    		if(!j3mRoot.exists())
    			j3mRoot.mkdir();
    		
    		j3mdescriptor = new J3MDescriptor(root);
    		j3mdescriptor.put("pgpKeyFingerprint", pgpKeyFingerprint);
    		if(atomize()) {
    			j3mdescriptor.finalize();
    			ioCipherService.copyFolder(root);
    			ioCipherService.moveFileToIOCipher(file, root.getName(), true);
    		}
			
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	private boolean atomize() {
		for(int c=0; c<chunk_num; c++) {
			JSONObject chunk_description = new JSONObject();
			
			int offset = chunk_count * mode;
			
			byte[] j3mBytes = new byte[(int) Math.min(mode, fileBytes.length - offset)];
			
			// write offset + mode bytes to "blob" field as base 64 encoded string
			System.arraycopy(fileBytes, offset, j3mBytes, 0, j3mBytes.length);
			try {
				chunk_description.put(Constants.J3M.Metadata.SOURCE, file.getName());
				chunk_description.put(Constants.J3M.Metadata.INDEX, chunk_count);
				chunk_description.put(Constants.J3M.Metadata.BLOB, Base64.encodeToString(j3mBytes, Base64.DEFAULT));
				chunk_description.put(Constants.J3M.Metadata.LENGTH, chunk_description.getString(Constants.J3M.Metadata.BLOB).length());
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return false;
			}
			
			// save output
			try {
				FileWriter output = new FileWriter(new File(j3mRoot, chunk_count + "_.j3mtorrent"));
				output.write(chunk_description.toString());
				output.flush();
				output.close();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return false;
			}
						
			chunk_count++;
		}
		
		
		try {
			// update descriptor
			j3mdescriptor.put("originalHash", root.getName());
			j3mdescriptor.put("versionLocation", "/" + root.getName() + "/" + file.getName());
			j3mdescriptor.put("mediaType", mediaType);
			j3mdescriptor.put("totalBytesExpected", file.length());
			j3mdescriptor.put("j3mBytesExpected", getFileContentSize());
			j3mdescriptor.put("timestampCreated", timestampCreated);
			j3mdescriptor.put(Constants.J3M.Metadata.NUM_CHUNKS, chunk_num);
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return false;
		}
		

		return true;
	}
	
	public J3MDescriptor getDescriptor() {
		return j3mdescriptor;
	}
	
	private long getFileContentSize() {
		long size = 0;
		for(File f : j3mRoot.listFiles()) {
			size += f.length();
		}
		Log.d(LOG, "file contents: " + size);
		return size;
	}
	
	public static final class J3MDescriptor extends JSONObject {
		File j3mdescriptor, file, dump_folder;
		
		public J3MDescriptor(File root) {
			j3mdescriptor = new File(root, root.getName() + Constants.J3M.TORRENT_DESCRIPTOR_MIME_TYPE);
		}
		
		public void finalize() {
			try {
				FileWriter output = new FileWriter(j3mdescriptor);
				output.write(this.toString());
	    		output.flush();
	    		output.close();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
		}
	}
	
	public final static class J3MPackage {
		public String j3m, url;
		public long pkcs12Id;
		
		public J3MPackage(J3M j3m, String url, long pkcs12Id) {
			this.j3m = j3m.j3mdescriptor.toString();
			this.pkcs12Id = pkcs12Id;
			this.url = url;
		}
	}
}
