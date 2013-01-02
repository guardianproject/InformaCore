package org.witness.informacam.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.witness.informacam.utils.Constants.Crypto;

import android.util.Base64;
import android.util.Log;

public class EncryptionUtility {
	
	@SuppressWarnings("deprecation")
	public final static String encrypt(byte[] data, byte[] publicKey) {
		try {
			BouncyCastleProvider bc = new BouncyCastleProvider();
			int bufferSize = 1 << 16;
			
			Security.addProvider(bc);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			OutputStream aos = new ArmoredOutputStream(baos);
			
			PGPEncryptedDataGenerator edg = new PGPEncryptedDataGenerator(PGPEncryptedData.AES_256, true, new SecureRandom(), bc);
			edg.addMethod(KeyUtility.extractPublicKeyFromBytes(publicKey));
			OutputStream encOs = edg.open(aos, new byte[bufferSize]);
			
			PGPCompressedDataGenerator cdg = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
			OutputStream compOs = cdg.open(encOs);
			
			PGPLiteralDataGenerator ldg = new PGPLiteralDataGenerator();
			OutputStream litOs = ldg.open(compOs, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, new Date(System.currentTimeMillis()), new byte[bufferSize]);
			
			InputStream is = new ByteArrayInputStream(data);
			byte[] buf = new byte[bufferSize];
			
			int len;
			while((len = is.read(buf)) > 0)
				litOs.write(buf, 0, len);
			
			litOs.flush();
			litOs.close();
			ldg.close();
			
			compOs.flush();
			compOs.close();
			cdg.close();
			
			encOs.flush();
			encOs.close();
			edg.close();
			
			baos.flush();
			aos.close();
			baos.close();
			
			is.close();
			
			String encrypted = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
			Log.d(Crypto.LOG, encrypted);
			return encrypted;
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			return null;
		} catch (PGPException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
