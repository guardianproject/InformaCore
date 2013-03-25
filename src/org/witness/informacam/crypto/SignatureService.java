package org.witness.informacam.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.witness.informacam.utils.LogPack;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.App.Crypto.Signatures;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SignatureService extends Service {
	private final IBinder binder = new LocalBinder();
	private static SignatureService signatureService = null;
	
	private PGPSecretKey secretKey = null;
	private PGPPrivateKey privateKey = null;
	private PGPPublicKey publicKey = null;
	private String authKey = null;
	
	private final static String LOG = App.Crypto.LOG;
	
	public class LocalBinder extends Binder {
		public SignatureService getService() {
			return SignatureService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(LOG, "started.");
		
		signatureService = this;
		sendBroadcast(new Intent().setAction(Actions.ASSOCIATE_SERVICE).putExtra(Codes.Keys.SERVICE, Codes.Routes.SIGNATURE_SERVICE));
		
	}
	
	@SuppressWarnings({ "unused", "deprecation" })
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
	
	public static SignatureService getInstance() {
		return signatureService;
	}

}
