package org.witness.informacam.j3m;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.content.ContentValues;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

public class J3M {
	File file, root, j3mRoot;
	J3MDescriptor j3mdescriptor;
	
	byte[] fileBytes;
	static String LOG = Constants.J3M.LOG;
	int chunk_num, chunk_count = 0;
	int mode = 0;
	int mediaType;
	long timestampCreated;
	String pgpFingerprint, derivativeRoot;
	
	IOCipherService ioCipherService = IOCipherService.getInstance();
	UploaderService uploaderService = UploaderService.getInstance();
	
	public J3M(String pgpFingerprint, ContentValues cv, File file) {
		this.file = file;
		this.pgpFingerprint = pgpFingerprint;
		timestampCreated = cv.getAsLong(Media.Keys.TIME_CAPTURED);
		mediaType = cv.getAsInteger(Media.Keys.TYPE);
		derivativeRoot = cv.getAsString(Media.Keys.DERIVATIVE_ROOT);
		
		int offset = 0;
		int numRead = 0;
		
		try {
			root = new File(Storage.FileIO.DUMP_FOLDER, MediaHasher.hash(file, "SHA-1"));
			if(!root.exists())
				root.mkdir();
						
			fileBytes = new byte[(int) file.length()];
			mode = Constants.J3M.Chunks.EXTRA_EXTRA_EXTRA_LARGE;
			
			InputStream fis = new FileInputStream(file);
			while(offset < fileBytes.length && (numRead = fis.read(fileBytes, offset, fileBytes.length - offset)) > 0)
    			offset += numRead;
    		fis.close();
    		
    		chunk_num = (int) Math.ceil(fileBytes.length/mode) + 1;
    		
    		j3mRoot = new File(root, Constants.J3M.DUMP_FOLDER);
    		if(!j3mRoot.exists())
    			j3mRoot.mkdir();
    		
    		j3mdescriptor = new J3MDescriptor(root);
    		j3mdescriptor.put("pgpKeyFingerprint", pgpFingerprint);
    		
    		if(atomize()) {
    			j3mdescriptor.finalize();
    			ioCipherService.copyFolder(root, true);
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
	
	public String getThumbnail() {
		return derivativeRoot + "/thumbnail.jpg";
	}
	
	public String getBase() {
		return root.getName();
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
	
	public static final class J3MManifest extends JSONObject {
		info.guardianproject.iocipher.File root;
		
		public J3MManifest(J3MPackage j3mpackage, String authToken) {
			root = new info.guardianproject.iocipher.File(j3mpackage.root);			
			
			// XXX: why does iocipher not see the files?
			info.guardianproject.iocipher.File j3m_root = new info.guardianproject.iocipher.File(root, "j3m");
			IOCipherService.getInstance().walk(j3m_root);
			
			try {
				put(Media.Manifest.Keys.DERIVATIVE_ROOT, j3mpackage.derivativeRoot);
				put(Transport.Keys.URL, j3mpackage.url);
				put(Transport.Manifest.Keys.AUTH_TOKEN, authToken);
				put(Transport.Keys.CERTS, j3mpackage.pkcs12Id);
				put(Transport.Manifest.Keys.TRUSTED_DESTINATION_DISPLAY_NAME, j3mpackage.displayName);
				put(Crypto.PGP.Keys.PGP_FINGERPRINT, j3mpackage.pgpFingerprint);
				put(Media.Keys.J3M_BASE, root.getName());
				put(Media.Manifest.Keys.TOTAL_CHUNKS, j3mpackage.chunk_num);
				put(Transport.Manifest.Keys.LAST_TRANSFERRED, -1);
				Log.d(Constants.J3M.LOG, "created new manifest:\n" + toString());
			} catch (JSONException e) {
				Log.e(Constants.J3M.LOG, e.toString()); 
				e.printStackTrace();
			}
		}
		
		@SuppressWarnings("unchecked")
		public J3MManifest(JSONObject j) {
			try {
				Log.d(Constants.J3M.LOG, "opened up manifest:\n" + j.toString());
				Iterator<String> iIt = j.keys();
				while(iIt.hasNext()) {
					String key = iIt.next();
					put(key, j.get(key));
				}					
			} catch (JSONException e) {
				Log.e(Constants.J3M.LOG, e.toString());
				e.printStackTrace();
			}
		}

		public void save(Handler h) {
			DatabaseHelper dh = DatabaseService.getInstance().getHelper();
			SQLiteDatabase db = DatabaseService.getInstance().getDb();
			
			try {
				Log.d(Constants.J3M.LOG, "saved over manifest?:\n" + this.toString());
				
				ContentValues cv = new ContentValues();
				cv.put(Media.Keys.J3M_MANIFEST, toString());
				
				dh.setTable(db, Tables.Keys.MEDIA);
				db.update(dh.getTable(), cv, Media.Keys.J3M_BASE + " =?", new String[] {getString(Media.Keys.J3M_BASE)});
				
				if(h != null) {
					h.postDelayed(new Runnable() {
						@Override
						public void run() {
							Log.d(Constants.J3M.LOG, "confirm: GREAT");
						}
					}, 3000);
				}
			} catch(JSONException e) {}
			
		}

		public void save() {
			save(null);
		}
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
		public String pgpFingerprint, derivativeRoot;
		public String j3m, url, root, displayName, thumbnail;
		public long pkcs12Id;
		public int chunk_num;
		
		public J3MPackage(J3M j3m, String url, long pkcs12Id, String displayName) {
			this.j3m = j3m.j3mdescriptor.toString();
			this.pgpFingerprint = j3m.pgpFingerprint;
			this.pkcs12Id = pkcs12Id;
			this.url = url;
			this.root = j3m.root.getName();
			this.chunk_num = j3m.chunk_num;
			this.displayName = displayName;
			this.thumbnail = j3m.getThumbnail();
			this.derivativeRoot = j3m.derivativeRoot;
		}
	}
}