package org.witness.informacam.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class InformaTrustManager implements X509TrustManager {
	private KeyStore keyStore;
	private X509TrustManager defaultTrustManager;
	private X509TrustManager appTrustManager;
	
	private SQLiteDatabase db;
	private DatabaseHelper dh;
	
	byte[] keyStored = null;
	String pwd;
	long lastUpdate;
	
	public InformaTrustManager(Context context) {		
		dh = new DatabaseHelper(context);
		db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(context).getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		dh.setTable(db, Tables.Keys.SETUP);
		Cursor c = dh.getValue(db, new String[] {Settings.Device.Keys.AUTH_KEY}, BaseColumns._ID, 1);
		if(c != null && c.getCount() > 0) {
			c.moveToFirst();
			pwd = c.getString(c.getColumnIndex(Settings.Device.Keys.AUTH_KEY));
			c.close();
		}
		
		dh.setTable(db, Tables.Keys.KEYSTORE);
		Cursor d = dh.getValue(db, new String[] {
				BaseColumns._ID,
				Crypto.Keystore.Keys.CERTS, 
				Crypto.Keystore.Keys.TIME_MODIFIED
			}, BaseColumns._ID, 1);
		if(d != null && d.getCount() > 0) {
			d.moveToFirst();
			keyStored = d.getBlob(d.getColumnIndex(Crypto.Keystore.Keys.CERTS));
			lastUpdate = d.getLong(d.getColumnIndex(Crypto.Keystore.Keys.TIME_MODIFIED));
			d.close();
		}
		
		loadKeyStore();
		defaultTrustManager = getTrustManager(false);
		appTrustManager = getTrustManager(true);
	}
	
	private X509TrustManager getTrustManager(boolean withKeystore) {
		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			if(withKeystore)
				tmf.init(keyStore);
			else
				tmf.init((KeyStore) null);
			for(TrustManager t : tmf.getTrustManagers())
				if(t instanceof X509TrustManager)
					return (X509TrustManager) t;
		} catch (KeyStoreException e) {
			Log.e(Crypto.LOG, "key store exception: " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, "no such algo exception: " + e.toString());
		}
		return null;
	}
	
	private void loadKeyStore() {
		try {
			keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		} catch(KeyStoreException e) {
			Log.e(Crypto.LOG, "key store exception: " + e.toString());
		}
		
		try {
			keyStore.load(null, null);
			if(keyStored != null)
				keyStore.load(new ByteArrayInputStream(keyStored), pwd.toCharArray());
		} catch(CertificateException e) {
			Log.e(Crypto.LOG, "certificate exception: " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, "no such algo exception: " + e.toString());
		} catch (IOException e) {
			Log.e(Crypto.LOG, "IOException: " + e.toString());
		}
	}
	
	private void storeCertificate(X509Certificate[] chain) {
		try {
			for(X509Certificate cert : chain)
				keyStore.setCertificateEntry(cert.getSubjectDN().toString(), cert);
		} catch(KeyStoreException e) {
			Log.e(Crypto.LOG, "keystore exception: " + e.toString());
		}
		
		appTrustManager = getTrustManager(true);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			keyStore.store(baos, pwd.toCharArray());
			updateKeyStore(baos.toByteArray());
			Log.d(Crypto.LOG, "new key encountered!  length: " + baos.size());
		} catch(KeyStoreException e) {
			Log.e(Crypto.LOG, "keystore exception: " + e.toString());	
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, "no such algo exception: " + e.toString());
		} catch (IOException e) {
			Log.e(Crypto.LOG, "IOException: " + e.toString());
		} catch (CertificateException e) {
			Log.e(Crypto.LOG, "Certificate Exception: " + e.toString());
		}
	}
	
	private void updateKeyStore(byte[] newKey) {
		boolean firstKey = (keyStored == null ? true : false);
		keyStored = newKey;
		lastUpdate = System.currentTimeMillis();
		
		dh.setTable(db, Tables.Keys.KEYSTORE);
		ContentValues cv = new ContentValues();
		cv.put(Crypto.Keystore.Keys.CERTS, keyStored);
		cv.put(Crypto.Keystore.Keys.TIME_MODIFIED, lastUpdate);
		
		if(firstKey)
			db.insert(dh.getTable(), null, cv);
		else
			db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Long.toString(1)});
	}
	
	private boolean isCertKnown(X509Certificate cert) {
		try {
			return keyStore.getCertificateAlias(cert) != null;
		} catch(KeyStoreException e) {
			return false;
		}
	}
	
	private boolean isExpiredException(Throwable e) {
		do {
			if(e instanceof CertificateExpiredException)
				return true;
			e = e.getCause();
		} while(e != null);
		return false;
	}
	
	private void checkCertificateTrusted(X509Certificate[] chain, String authType, boolean isServer) throws CertificateException {
		try {
			if(isServer)
				appTrustManager.checkServerTrusted(chain, authType);
			else
				appTrustManager.checkClientTrusted(chain, authType);
		} catch(CertificateException e) {
			if(isExpiredException(e))
				return;
			if(isCertKnown(chain[0]))
				return;
			
			try {
				if(isServer)
					defaultTrustManager.checkServerTrusted(chain, authType);
				else
					defaultTrustManager.checkClientTrusted(chain, authType);
			} catch(CertificateException ce) {
				storeCertificate(chain);
			}
		}
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		checkCertificateTrusted(chain, authType, false);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		checkCertificateTrusted(chain, authType, true);
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return defaultTrustManager.getAcceptedIssuers();
	}
	
}
