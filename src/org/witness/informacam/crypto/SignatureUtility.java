package org.witness.informacam.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.sqlcipher.database.SQLiteDatabase;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Crypto.Signatures;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Base64;
import android.util.Log;


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
	
	public boolean isVerified(final LogPack data) {
		boolean isVerified = false;

		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<Boolean> future = ex.submit(new Callable<Boolean>() {
			
			@SuppressWarnings("deprecation")
			@Override
			public Boolean call() throws Exception {
				try {
					byte[] signedData = (byte[]) data.remove(Signatures.Keys.SIGNATURE);
					ByteArrayInputStream sd = new ByteArrayInputStream(signedData);				
					
					InputStream is = PGPUtil.getDecoderStream(sd);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
							
					PGPObjectFactory objFactory = new PGPObjectFactory(is);
					PGPCompressedData cd = (PGPCompressedData) objFactory.nextObject();
					
					objFactory = new PGPObjectFactory(cd.getDataStream());
					
					PGPOnePassSignatureList sigList_o = (PGPOnePassSignatureList) objFactory.nextObject();
					PGPOnePassSignature sig = sigList_o.get(0);
					
					PGPLiteralData ld = (PGPLiteralData) objFactory.nextObject();
					InputStream literalIn = ld.getInputStream();
					
					sig.initVerify(publicKey, new BouncyCastleProvider());
					
					int read;
					while((read = literalIn.read()) > 0) {
						sig.update((byte) read);
						baos.write(read);
					}
					
					PGPSignatureList sigList = (PGPSignatureList) objFactory.nextObject();
					
					if(sig.verify(sigList.get(0)) && data.toString().equals(new String(baos.toByteArray()))) {
						baos.close();			
						return true;
					} else {
						baos.close();
						return false;
					}
				} catch(NullPointerException e) {
					return false;
				}
			}
		});
		
		try {
			isVerified = future.get();
		} catch (InterruptedException e) {}
		catch (ExecutionException e) {
			e.printStackTrace();
		}
		
		ex.shutdown();
		
		return isVerified;
	}
	
	public byte[] signData(final byte[] data) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<byte[]> future = ex.submit(new Callable<byte[]>() {

			@Override
			public byte[] call() throws Exception {
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
