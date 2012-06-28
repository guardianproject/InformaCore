package org.witness.informa.utils.secure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.SignatureException;
import java.util.Date;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.witness.informa.utils.InformaConstants;

import android.util.Base64;
import android.util.Log;

public class SignatureUtil {
	private PGPSecretKey secretKey = null;
	private PGPPrivateKey privateKey = null;
	private PGPPublicKey publicKey = null;
	private BouncyCastleProvider bc;
	private String pwd, LOG;
	int buffSize = 1 << 16;
	
	public static SignatureUtil signatureUtil;
	
	public SignatureUtil(byte[] secret_bytes, String pwd) {
		this.pwd = pwd;
		LOG = InformaConstants.TAG;
		Log.d(LOG, "Signature Utility Started!");
		initKey(secret_bytes);
	}
	
	@SuppressWarnings({ "unchecked", "deprecation" })
	private void initKey(byte[] pk) {
		bc = new BouncyCastleProvider();
		try {
			PGPSecretKeyRingCollection pkrc = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(pk)));
			Iterator<PGPSecretKeyRing> rIt = pkrc.getKeyRings();
			while(rIt.hasNext()) {
				PGPSecretKeyRing pkr = (PGPSecretKeyRing) rIt.next();
				Iterator<PGPSecretKey> kIt = pkr.getSecretKeys();
				while(privateKey == null && kIt.hasNext()) {
					secretKey = kIt.next();
					privateKey = secretKey.extractPrivateKey(pwd.toCharArray(), bc);
					publicKey = secretKey.getPublicKey();
					
					signatureUtil = this;
					return;
				}
			}
		} catch (IOException e) {
			signatureUtil = null;
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (PGPException e) {
			signatureUtil = null;
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
	}
	
	public static SignatureUtil getSignatureUtil() {
		return signatureUtil;
	}
	
	public String signData(byte[] data) {
		// BASE 64 ENCODED OK?
		if(signatureUtil != null) {
	    	Security.addProvider(bc);
	    	
	    	ByteArrayInputStream bais = new ByteArrayInputStream(data);
	    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    	
	    	try {
	    		OutputStream targetOut = new ArmoredOutputStream(baos);
	    		
	    		PGPCompressedDataGenerator cdGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
	    		OutputStream compressedOut = cdGen.open(targetOut, new byte[buffSize]);
	    		
				PGPSignatureGenerator sGen = new PGPSignatureGenerator(publicKey.getAlgorithm(), PGPUtil.SHA1, bc);
				sGen.initSign(PGPSignature.BINARY_DOCUMENT, privateKey);
				Iterator<String> uId = secretKey.getUserIDs();
				while(uId.hasNext()) {
					String userId = (String) uId.next();
					
					PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
					spGen.setSignerUserID(false, userId);
					sGen.setHashedSubpackets(spGen.generate());
					
					// we only need the first userId
					break;
				}
				sGen.generateOnePassVersion(false).encode(compressedOut);
				
				PGPLiteralDataGenerator ldGen = new PGPLiteralDataGenerator();
				OutputStream literalOut = ldGen.open(compressedOut, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, new Date(System.currentTimeMillis()), new byte[buffSize]);
				
				byte[] buf = new byte[buffSize];
				int read;
				
				while((read = bais.read(buf, 0, buf.length)) > 0) {
					literalOut.write(buf, 0, read);
					sGen.update(buf, 0, read);
				}
				
				literalOut.close();
				ldGen.close();
				
				sGen.generate().encode(compressedOut);
				compressedOut.close();
				cdGen.close();
				
				bais.close();
				
				targetOut.close();
				return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
			} catch (NoSuchAlgorithmException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (PGPException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (SignatureException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		return null;
		
	}
}
