package org.witness.informacam.j3m;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.crypto.SignatureService;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Transport;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.J3M.Keys;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.content.ContentValues;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

public class J3M {
	File file, root, j3mRoot;
	info.guardianproject.iocipher.File file_, root_, j3mRoot_;
	public J3MManifest j3mmanifest;

	byte[] fileBytes;
	static String LOG = Constants.J3M.LOG;
	int chunk_num, chunk_count = 0;
	int mode = 0;
	int mediaType;
	long timestampCreated;
	String pgpFingerprint, derivativeRoot;

	IOCipherService ioCipherService = IOCipherService.getInstance();
	UploaderService uploaderService = UploaderService.getInstance();

	public J3M(J3MManifest j3mmanifest) {
		Log.d(J3M.LOG, j3mmanifest.toString());
		

		try {
			mode = Constants.J3M.CHUNKS.get(uploaderService.getMode());
			Log.d(LOG, "Chunk size: " + mode);

			j3mmanifest.remove(Media.Manifest.Keys.WHOLE_UPLOAD);
			j3mmanifest.remove(Media.Manifest.Keys.WHOLE_UPLOAD_PATH);

			JSONObject j3mdescriptor = j3mmanifest.getJSONObject(Keys.DESCRIPTOR);
			j3mdescriptor.remove(Media.Manifest.Keys.WHOLE_UPLOAD);

			int offset = 0;
			int numRead = 0;

			info.guardianproject.iocipher.File file = ioCipherService.getFile(j3mdescriptor.getString("versionLocation"));
			fileBytes = IOUtility.getBytesFromFile(file);
			root_ = ioCipherService.getFile(j3mmanifest.getString(Media.Manifest.Keys.J3MBASE));

			j3mRoot_ = ioCipherService.getFile(root_, Constants.J3M.DUMP_FOLDER);
			for(info.guardianproject.iocipher.File f : ioCipherService.walk(j3mRoot_))
				f.delete();

			if(mode != Constants.J3M.Chunks.WHOLE) {
				InputStream fis = new info.guardianproject.iocipher.FileInputStream(file);
				while(offset < fileBytes.length && (numRead = fis.read(fileBytes, offset, fileBytes.length - offset)) > 0)
					offset += numRead;
				fis.close();

				chunk_num = (int) Math.ceil(fileBytes.length/mode) + 1;
			}

			if(mode != Constants.J3M.Chunks.WHOLE) {
				if(atomize(true)) {
					try {
						j3mdescriptor.put("j3mBytesExpected", getFileContentSize());
						j3mdescriptor.put(Constants.J3M.Metadata.NUM_CHUNKS, chunk_num);
						
						j3mmanifest.put(Media.Manifest.Keys.TOTAL_CHUNKS, chunk_num);
						j3mmanifest.put(Transport.Manifest.Keys.LAST_TRANSFERRED, -1);
					} catch(JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
				
			} else {
				try {
					j3mdescriptor.put(Constants.J3M.Metadata.WHOLE_UPLOAD, true);
				} catch(JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}

				j3mmanifest.put(Media.Manifest.Keys.WHOLE_UPLOAD, true);
				j3mmanifest.put(Media.Manifest.Keys.WHOLE_UPLOAD_PATH, root.getName() + "/" + file.getName());
			}

			j3mmanifest.put(Keys.DESCRIPTOR, j3mdescriptor);
			j3mmanifest.save();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public J3M(String pgpFingerprint, ContentValues cv, File file) {
		this.file = file;
		this.pgpFingerprint = pgpFingerprint;
		timestampCreated = cv.getAsLong(Media.Keys.TIME_CAPTURED);
		mediaType = cv.getAsInteger(Media.Keys.TYPE);
		derivativeRoot = cv.getAsString(Media.Keys.DERIVATIVE_ROOT);

		int offset = 0;
		int numRead = 0;

		// TODO: not on the flash, pls.  move to IOCipher.
		try {
			root = new File(Storage.FileIO.DUMP_FOLDER, MediaHasher.hash(file, "SHA-1"));
			if(!root.exists())
				root.mkdir();

			fileBytes = new byte[(int) file.length()];
			Log.d(LOG, "file is " + file.length());

			mode = Constants.J3M.CHUNKS.get(uploaderService.getMode());
			Log.d(LOG, "Chunk size: " + mode);

			j3mRoot = new File(root, Constants.J3M.DUMP_FOLDER);
			if(!j3mRoot.exists())
				j3mRoot.mkdir();

			j3mmanifest = new J3MManifest(root.getName());
			j3mmanifest.put(Keys.FINGERPRINT, pgpFingerprint);
			j3mmanifest.put(Keys.THUMBNAIL, getThumbnail());

			if(mode != Constants.J3M.Chunks.WHOLE) {
				InputStream fis = new FileInputStream(file);
				while(offset < fileBytes.length && (numRead = fis.read(fileBytes, offset, fileBytes.length - offset)) > 0)
					offset += numRead;
				fis.close();

				chunk_num = (int) Math.ceil(fileBytes.length/mode) + 1;
			}

			JSONObject j3mdescriptor = setDescriptor();
			if(mode == Constants.J3M.Chunks.WHOLE || atomize()) {
				ioCipherService.copyFolder(root, true);
				ioCipherService.moveFileToIOCipher(file, root.getName(), true);
			}

			if(mode != Constants.J3M.Chunks.WHOLE) {
				try {
					j3mdescriptor.put("j3mBytesExpected", getFileContentSize());
					j3mdescriptor.put(Constants.J3M.Metadata.NUM_CHUNKS, chunk_num);
				} catch(JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}

				j3mmanifest.put(Media.Manifest.Keys.TOTAL_CHUNKS, chunk_num);
				j3mmanifest.put(Transport.Manifest.Keys.LAST_TRANSFERRED, -1);
			} else {
				try {
					j3mdescriptor.put(Constants.J3M.Metadata.WHOLE_UPLOAD, true);
				} catch(JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}

				j3mmanifest.put(Media.Manifest.Keys.WHOLE_UPLOAD, true);
				j3mmanifest.put(Media.Manifest.Keys.WHOLE_UPLOAD_PATH, root.getName() + "/" + file.getName());
			}

			j3mmanifest.put(Keys.DESCRIPTOR, j3mdescriptor);
			j3mmanifest.save();

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

	private String getThumbnail() {
		return derivativeRoot + "/thumbnail.jpg";
	}

	public String getBase() {
		return root.getName();
	}

	private boolean atomize() {
		return atomize(false);
	}

	
	private boolean atomize(boolean inIOCipher) {
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

			try {
				byte[] chunk_description_bytes = null;
				chunk_description_bytes = chunk_description.toString().getBytes();

				// TODO: write directly to IOCipher if possible

				if(!inIOCipher) {
					FileOutputStream output = new FileOutputStream(new File(j3mRoot, chunk_count + "_.j3mtorrent"));
					output.write(chunk_description_bytes);
					output.flush();
					output.close();
				} else {
					info.guardianproject.iocipher.FileOutputStream output = new info.guardianproject.iocipher.FileOutputStream(new info.guardianproject.iocipher.File(j3mRoot_ + "/" + chunk_count + "_.j3mtorrent"));
					output.write(chunk_description_bytes);
					output.flush();
					output.close();
				}

			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return false;
			} catch(OutOfMemoryError e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return false;
			} 

			chunk_count++;
		}

		return true;
	}

	private JSONObject setDescriptor() {
		JSONObject j3mdescriptor = new JSONObject();
		try {
			// update descriptor
			if(j3mmanifest.has(Keys.DESCRIPTOR))
				j3mdescriptor = j3mmanifest.getJSONObject(Keys.DESCRIPTOR);

			j3mdescriptor.put("pgpKeyFingerprint", this.pgpFingerprint);
			j3mdescriptor.put("originalHash", root.getName());
			j3mdescriptor.put("versionLocation", "/" + root.getName() + "/" + file.getName());
			j3mdescriptor.put("mediaType", mediaType);
			j3mdescriptor.put("totalBytesExpected", (int) file.length());
			j3mdescriptor.put("timestampCreated", timestampCreated);
			j3mdescriptor.put("dataSignature", new String(SignatureService.getInstance().signData(IOUtility.getBytesFromFile(file))));
			Log.d(LOG, "this descriptor:\n" + j3mdescriptor.toString());

			return j3mdescriptor;
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return null;
	}

	private long getFileContentSize() {
		long size = 0;
		for(File f : j3mRoot.listFiles()) {
			size += f.length();
		}
		return size;
	}

	public static final class J3MManifest extends JSONObject {
		info.guardianproject.iocipher.File root, j3m_root;

		public J3MManifest(String root_) {
			root = IOCipherService.getInstance().getFile(root_);
			j3m_root = IOCipherService.getInstance().getFile(root, "j3m");

			try {
				put(Keys.ROOT, root.getAbsolutePath());
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

				ContentValues cv = new ContentValues();
				cv.put(Media.Keys.J3M_MANIFEST, toString());

				dh.setTable(db, Tables.Keys.MEDIA);
				db.update(dh.getTable(), cv, Media.Keys.J3M_BASE + " =?", new String[] {getString(Media.Keys.J3M_BASE)});
				Log.d(Storage.LOG, "saving over manifest\n" + cv.toString());
				if(h != null) {
					h.postDelayed(new Runnable() {
						@Override
						public void run() {

						}
					}, 3000);
				}
			} catch(JSONException e) {
				Log.e(Storage.LOG, e.toString());
			}

		}

		public void save() {
			save(null);
		}
	}
}