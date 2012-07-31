package org.witness.informacam.crypto;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.sqlcipher.database.SQLiteDatabase;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;


public class SignatureUtility {
	public static SignatureUtility signatureUtility;
	
	private PGPSecretKey secretKey = null;
	private PGPPrivateKey privateKey = null;
	private PGPPublicKey publicKey = null;
	private String authKey = null;
	
	public SignatureUtility(Activity a) {
		DatabaseHelper dh = new DatabaseHelper(a);
		SQLiteDatabase db = dh.getReadableDatabase(PreferenceManager.getDefaultSharedPreferences(a).getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		dh.setTable(db, Tables.Keys.SETUP);
		Cursor k = dh.getValue(db, new String[] {Device.Keys.AUTH_KEY, Device.Keys.SECRET_KEY}, BaseColumns._ID, 1);
		if(k != null && k.moveToFirst()) {
			try {
				initKey(k.getBlob(k.getColumnIndex(Device.Keys.SECRET_KEY)), k.getString(k.getColumnIndex(Device.Keys.AUTH_KEY)));
			} catch(PGPException e) {}
			k.close();
		}
		
		db.close();
		dh.close();
		
		signatureUtility = this;
	}
	
	public static SignatureUtility getInstance() {
		return signatureUtility;
	}
	
	@SuppressWarnings("deprecation")
	private void initKey(byte[] sk, String authKey) throws PGPException {
		this.authKey = authKey;
		secretKey = KeyUtility.extractSecretKey(sk);
		privateKey = secretKey.extractPrivateKey(this.authKey.toCharArray(), new BouncyCastleProvider());
		publicKey = secretKey.getPublicKey();
	}
	
	public String signData(final byte[] data) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<String> future = ex.submit(new Callable<String>() {

			@Override
			public String call() throws Exception {
				return KeyUtility.applySignature(data, secretKey, publicKey, privateKey);
			}
		});
		
		try {
			return future.get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			return null;
		}
	}

}
