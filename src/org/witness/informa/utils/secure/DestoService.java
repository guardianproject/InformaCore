package org.witness.informa.utils.secure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.sqlcipher.database.SQLiteDatabase;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Device;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.io.Uploader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

/*
 * This class takes an array of email addresses
 * and returns a mapping of emails and desto URIs
 * to calling function.
 * 
 * If any desto is not found in the database,
 * it will look up the desto's URI at the GuardianProject repo 
 */
public class DestoService {
	DatabaseHelper dh;
	SQLiteDatabase db;
	SharedPreferences sp;
	ArrayList<Map<String, Map<String, Object>>> destoResults;
	ArrayList<String> pubkeyQuery, destoQuery;
	Uploader uploader;
	String pubKeyQueryemail;
	
	public DestoService(Context c) {
		sp = PreferenceManager.getDefaultSharedPreferences(c);
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		destoQuery = new ArrayList<String>();
		pubkeyQuery = new ArrayList<String>();
		
		destoResults = new ArrayList<Map<String, Map<String, Object>>>();
		
		uploader = Uploader.getUploader();
	}
	
	private void checkTrustedDestinationServerIsAvailable(String email) {
		dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
		Cursor td = dh.getValue(db, new String[] {TrustedDestinations.DESTO}, TrustedDestinations.EMAIL, email);
		if(td != null && td.getCount() == 1) {
			td.moveToFirst();
			if(td.getString(td.getColumnIndex(TrustedDestinations.DESTO)) != null) {
				Map<String, Object> desto = new HashMap<String, Object>();
				desto.put(TrustedDestinations.DESTO, td.getString(td.getColumnIndex(TrustedDestinations.DESTO)));
				
				Map<String, Map<String, Object>> res = new HashMap<String, Map<String, Object>>();
				res.put(email, desto);
				destoResults.add(res);
			} else {
				destoQuery.add(email);
			}			
			td.close();
		} else {
			destoQuery.add(email);
		}
	}
	
	private void checkPublicKeyIsAvailable(String email) {
		dh.setTable(db, Tables.KEYRING);
		Cursor pk = dh.getValue(db, new String[] {Device.PUBLIC_KEY}, TrustedDestinations.EMAIL, email);
		if(pk != null) {
			pk.moveToFirst();
			Map<String, Object> desto = new HashMap<String, Object>();
			desto.put(TrustedDestinations.ENCRYPTION_KEY, pk.getBlob(pk.getColumnIndex(Device.PUBLIC_KEY)));
			
			Map<String, Map<String, Object>> res = new HashMap<String, Map<String, Object>>();
			res.put(email, desto);
			destoResults.add(res);
			
			pk.close();
		} else {
			pubkeyQuery.add(email);
		}
	}
	
	public ArrayList<Map<String, Map<String, Object>>> getDestos(ArrayList<Map<String, Long>> destos) {
		for(Map<String, Long> d : destos) {
			Entry<String, Long> desto = d.entrySet().iterator().next();
			checkTrustedDestinationServerIsAvailable(desto.getKey());
			checkPublicKeyIsAvailable(desto.getKey());
		}
		
		ExecutorService ex = Executors.newFixedThreadPool(100);
		if(destoQuery.size() > 0) {
			TrustedDestinationCallable tdCallable = new TrustedDestinationCallable(destoQuery);
			Future<Map<String, Object>> td = ex.submit(tdCallable);
			try {
				Iterator<Entry<String, Object>> it = td.get().entrySet().iterator();
				while(it.hasNext()) {
					Entry<String, Object> e = it.next();
					Map<String, Object> desto = new HashMap<String, Object>();
					desto.put(TrustedDestinations.DESTO, e.getValue());
					
					Map<String, Map<String, Object>> res = new HashMap<String, Map<String, Object>>();
					res.put(e.getKey(), desto);
					destoResults.add(res);
				}
			} catch (InterruptedException e) {
				Log.e(InformaConstants.TAG, "https error in queryRepo(): " + e.toString());
				e.printStackTrace();
			} catch (ExecutionException e) {
				Log.e(InformaConstants.TAG, "https error in queryRepo(): " + e.toString());
				e.printStackTrace();
			}
		}
		
		if(pubkeyQuery.size() > 0) {
			for(String pk : pubkeyQuery) {
				KeyServerCallable ksCallable = new KeyServerCallable(pk);
				Future<PGPPublicKey> ks = ex.submit(ksCallable);
				PGPPublicKey key;
				try {
					key = ks.get();
					if(key != null) {
						Map<String, Object> keyEntry = new HashMap<String, Object>();
						keyEntry.put(TrustedDestinations.ENCRYPTION_KEY, key.getEncoded());
						
						Map<String, Map<String, Object>> res = new HashMap<String, Map<String, Object>>();
						res.put(pk, keyEntry);
						destoResults.add(res);
					}
				} catch (InterruptedException e) {
					Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
					e.printStackTrace();
				} catch (ExecutionException e) {
					Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
					e.printStackTrace();
				} catch (IOException e) {
					Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
					e.printStackTrace();
				}
				
				
			}
		}
		
		db.close();
		dh.close();
		
		consolidate();
		
		return destoResults;
	}
	
	private void consolidate() {
		List<Map<String, Map<String, Object>>> destoResultsCopy = new CopyOnWriteArrayList<Map<String, Map<String, Object>>>();
		
		for(Map<String, Map<String, Object>> desto : destoResults) {
			Entry<String, Map<String, Object>> e = desto.entrySet().iterator().next();
			String email = e.getKey();
			Map<String, Object> knownTargets = e.getValue();			
			
			if(destoResultsCopy.size() > 0) {
				Iterator<Map<String, Map<String, Object>>> it = destoResultsCopy.iterator();
				while(it.hasNext()) {
					Map<String, Map<String, Object>> destoCopy = it.next();
					Entry<String, Map<String, Object>> eCopy = destoCopy.entrySet().iterator().next();
					String emailCopy = eCopy.getKey();
					Map<String, Object> knownTargetsCopy = eCopy.getValue();
					
					
					if(email.equals(emailCopy)) {
						Iterator<Entry<String, Object>> itK = knownTargets.entrySet().iterator();
						while(itK.hasNext()) {
							Entry<String, Object> kt = itK.next();
							knownTargetsCopy.put(kt.getKey(), kt.getValue());
						}
					} else {
						destoResultsCopy.add(desto);
					}
				}
			} else {
				destoResultsCopy.add(desto);
			}
		}
		
		destoResults.clear();
		destoResults.addAll(destoResultsCopy);
		Log.d(InformaConstants.TAG, "all destos: " + destoResults.toString());
	}
	
	private void updateKeyring(String destoEmail, PGPPublicKey key) throws NoSuchAlgorithmException, IOException {
		dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
		Cursor pk = dh.getValue(db, new String[] {TrustedDestinations.KEYRING_ID, TrustedDestinations.DISPLAY_NAME}, TrustedDestinations.EMAIL, destoEmail);
		if(pk != null) {
			ContentValues cv = new ContentValues();
			pk.moveToFirst();
			cv.put(TrustedDestinations.EMAIL, destoEmail);
			cv.put(TrustedDestinations.KEYRING_ID, key.getKeyID());
			cv.put(TrustedDestinations.DISPLAY_NAME, pk.getString(pk.getColumnIndex(TrustedDestinations.DISPLAY_NAME)));
			cv.put(Device.PUBLIC_KEY, key.getEncoded());
			cv.put(Device.PUBLIC_KEY_HASH, MediaHasher.hash(key.getEncoded(), "SHA-1"));
			pk.close();
			
			dh.setTable(db, Tables.KEYRING);
			db.insert(dh.getTable(), null, cv);
		}
	}
	
	private void updateTrustedDestination(Map<String, Object> desto) {
		dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
		Entry<String, Object> e = desto.entrySet().iterator().next();
		ContentValues cv = new ContentValues();
		cv.put(TrustedDestinations.DESTO, (String) e.getValue());	
		db.update(dh.getTable(), cv, TrustedDestinations.EMAIL + " = ?", new String[] {e.getKey()});
	}
	
	private class TrustedDestinationCallable implements Callable<Map<String, Object>> {
		ArrayList<String> emails;
		
		public TrustedDestinationCallable(ArrayList<String> emails) {
			this.emails = emails;
		}
		
		private Map<String, Object> parseResult(String result) throws JSONException {
			Map<String, Object> res = new HashMap<String, Object>();
			JSONArray jsond = (JSONArray) ((JSONObject) new JSONTokener(result).nextValue()).get(TrustedDestinations.HOOKUPS);
			for(int i=0; i<jsond.length(); i++) {
				JSONObject destoj = (JSONObject) jsond.get(i);
				res.put(destoj.getString(TrustedDestinations.EMAIL), destoj.getString(TrustedDestinations.DESTO));
				updateTrustedDestination(res);
			}
			return res;
		}
		
		private Map<String, Object> queryTrustedDestinationServer() {
			StringBuffer sb = new StringBuffer();
			for(String s : emails)
				sb.append("," + s);
			
			try {
				return parseResult(uploader.getDestos(sb.toString().substring(1)));
			} catch(Exception e) {
				Log.e(InformaConstants.TAG, "https error in queryRepo(): " + e.toString());
				return null;
			}
		}
		
		public Map<String, Object> call() {
			return queryTrustedDestinationServer();
		}
	}
	
	private class KeyServerCallable implements Callable<PGPPublicKey> {
		String email;
		
		public KeyServerCallable(String email) {
			this.email = email;
		}
		
		@SuppressWarnings("unchecked")
		private PGPPublicKey parseKey(byte[] keyBlock) throws IOException, PGPException {
			PGPPublicKeyRingCollection keyringCol = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(keyBlock)));
			PGPPublicKey key = null;
			Iterator<PGPPublicKeyRing> rIt = keyringCol.getKeyRings();
			while(key == null && rIt.hasNext()) {
				PGPPublicKeyRing keyring = (PGPPublicKeyRing) rIt.next();
				Iterator<PGPPublicKey> kIt = keyring.getPublicKeys();
				while(key == null && kIt.hasNext()) {
					PGPPublicKey k = (PGPPublicKey) kIt.next();
					if(k.isEncryptionKey())
						key = k;
				}
			}
			if(key == null)
				throw new IllegalArgumentException("there isn't an encryption key here.");
			return key;
		}
		
		private PGPPublicKey queryKeyServer() {
			try {
				PGPPublicKey newKey = parseKey(uploader.getPublicKey(this.email));
				updateKeyring(email, newKey);
				return newKey;
			} catch (IllegalStateException e) {
				Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
				e.printStackTrace();
				return null;
			} catch (URISyntaxException e) {
				Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
				e.printStackTrace();
				return null;
			} catch (PGPException e) {
				Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
				e.printStackTrace();
				return null;
			} catch (NoSuchAlgorithmException e) {
				Log.e(InformaConstants.TAG, "http error in queryKeyServer(): " + e.toString());
				e.printStackTrace();
				return null;
			}
		}
		
		public PGPPublicKey call() throws Exception {
			return queryKeyServer(); 
		}
	}
}
