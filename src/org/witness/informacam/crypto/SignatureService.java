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
import org.witness.informacam.models.credentials.ISecretKey;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.App.Crypto;
import org.witness.informacam.utils.Constants.App.Crypto.Signatures;

import android.content.Context;

public class SignatureService {
	
	private PGPSecretKey secretKey = null;
	private PGPPrivateKey privateKey = null;
	private PGPPublicKey publicKey = null;
	private String authKey = null;
	
	private static String LOG = Crypto.LOG;
	
	public SignatureService (Context context)
	{
		
	}
	
	
	@SuppressWarnings({"deprecation" })
	public void initKey(ISecretKey sk) throws PGPException {
		

		authKey = sk.secretAuthToken;
		secretKey = KeyUtility.extractSecretKey(sk.secretKey.getBytes());
		privateKey = secretKey.extractPrivateKey(authKey.toCharArray(), new BouncyCastleProvider());
		publicKey = secretKey.getPublicKey();
		
		sk = null;		
	}
	
	public boolean isVerified(final ILogPack data) {
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
			Logger.e(LOG, e);
			return null;
		} catch (ExecutionException e) {
			Logger.e(LOG, e);
			return null;
		}
	}
	

}
