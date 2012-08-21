package org.witness.informacam.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import net.sqlcipher.database.SQLiteDatabase;

import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class CertificateUtility {
	
	public static final void storeClientCertificate(Activity a, byte[] certificate) {
		
	}
	
	public static final void storeCertificate(Activity a, byte[] certificate) {
		DatabaseHelper dh = new DatabaseHelper(a);
		SQLiteDatabase db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(a).getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		byte[] keysStored = null;
		long lastUpdate = System.currentTimeMillis();
		boolean firstKey = true;
		
		String pwd = null;
		dh.setTable(db, Tables.Keys.SETUP);
		Cursor c = dh.getValue(db, new String[] {Settings.Device.Keys.AUTH_KEY}, BaseColumns._ID, 1);
		if(c != null && c.getCount() > 0) {
			c.moveToFirst();
			pwd = c.getString(c.getColumnIndex(Settings.Device.Keys.AUTH_KEY));
			c.close();
		} else
			return;
		
		dh.setTable(db, Tables.Keys.KEYSTORE);
		Cursor d = dh.getValue(db, new String[] {
				BaseColumns._ID,
				Crypto.Keystore.Keys.CERTS, 
				Crypto.Keystore.Keys.TIME_MODIFIED
			}, BaseColumns._ID, 1);
		if(d != null && d.getCount() > 0) {
			firstKey = false;
			d.moveToFirst();
			keysStored = d.getBlob(d.getColumnIndex(Crypto.Keystore.Keys.CERTS));
			lastUpdate = d.getLong(d.getColumnIndex(Crypto.Keystore.Keys.TIME_MODIFIED));
			d.close();
		}
		
		KeyStore keyStore = null;
		try {
			keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		} catch(KeyStoreException e) {
			Log.e(Crypto.LOG, "key store exception: " + e.toString());
		}
		
		try {
			keyStore.load(null, null);
			if(keysStored != null)
				keyStore.load(new ByteArrayInputStream(keysStored), pwd.toCharArray());
			
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			
			Collection<? extends Certificate> chain = cf.generateCertificates(new ByteArrayInputStream(certificate));
			for(Certificate cert : chain) {
				X509Certificate xCert = (X509Certificate) cert;
				keyStore.setCertificateEntry(xCert.getSubjectDN().toString(), xCert);
			}
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			keyStore.store(baos, pwd.toCharArray());
			
			keysStored = baos.toByteArray();
			
			ContentValues cv = new ContentValues();
			cv.put(Crypto.Keystore.Keys.CERTS, keysStored);
			cv.put(Crypto.Keystore.Keys.TIME_MODIFIED, lastUpdate);
			
			if(firstKey)
				db.insert(dh.getTable(), null, cv);
			else
				db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Long.toString(1)});
			
		} catch(CertificateException e) {
			Log.e(Crypto.LOG, "certificate exception: " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, "no such algo exception: " + e.toString());
		} catch (IOException e) {
			Log.e(Crypto.LOG, "IOException: " + e.toString());
		} catch (KeyStoreException e) {
			Log.e(Crypto.LOG, "keystore exception: " + e.toString());
			e.printStackTrace();
		}
		
		db.close();
		dh.close();
	}
	
}
