
package org.witness.informacam.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.client.ClientProtocolException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.transport.HttpUtility;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Settings.Device;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Base64;
import android.util.Log;

public class KeyUtility {
	public interface KeyFoundListener {
		public void onKeyFound(KeyServerResponse keyServerResponse);
	}
	
	public static PGPPublicKey makeKey(byte[] keyblock) throws IOException {
		PGPPublicKey key = null;
		PGPObjectFactory objFact = new PGPObjectFactory(PGPUtil.getDecoderStream(new ByteArrayInputStream(Base64.decode(keyblock, Base64.DEFAULT))));
		Object obj;
		
		while((obj = objFact.nextObject()) != null && key == null) {
			if(obj instanceof PGPPublicKey)
				key = (PGPPublicKey) obj;
		}
		return key;
	}
	
	public static PGPSecretKey extractSecretKey(byte[] keyblock) {
		PGPSecretKey secretKey = null;
		try {
			PGPSecretKeyRingCollection pkrc = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(Base64.decode(keyblock, Base64.DEFAULT))));
			Iterator<PGPSecretKeyRing> rIt = pkrc.getKeyRings();
			while(rIt.hasNext()) {
				PGPSecretKeyRing pkr = (PGPSecretKeyRing) rIt.next();
				Iterator<PGPSecretKey> kIt = pkr.getSecretKeys();
				while(secretKey == null && kIt.hasNext()) {
					secretKey = kIt.next();
				}
			}
			return secretKey;
		} catch(IOException e) {
			return null;
		} catch(PGPException e) {
			return null;
		}
	}
	
	public final static class KeyServerResponse extends JSONObject {
		public KeyServerResponse(PGPPublicKey key, String displayName, String email) {
			if(email == null) {
				Iterator<String> uIt = key.getUserIDs();
				while(uIt.hasNext()) {
					String[] id = uIt.next().split("<");
					displayName = id[0].substring(0, id[0].indexOf(" ("));
					email = id[1].substring(0, id[1].length() - 1);
				}
			}
			
			try {
				this.put(PGP.Keys.PGP_DISPLAY_NAME, displayName);
				this.put(PGP.Keys.PGP_EMAIL_ADDRESS, email);
				this.put(PGP.Keys.PGP_CREATION_DATE, key.getCreationTime().getTime());
				this.put(PGP.Keys.PGP_EXPIRY_DATE, key.getCreationTime().getTime() + key.getValidSeconds());
				this.put(PGP.Keys.PGP_FINGERPRINT, new String(Hex.encode(key.getFingerprint())));
				this.put(PGP.Keys.PGP_KEY_ID, key.getKeyID());
				this.put(Crypto.Keyring.Keys.PUBLIC_KEY, Base64.encodeToString(key.getEncoded(), Base64.DEFAULT));
				Log.d(Crypto.LOG, "key length: " + this.getString(Crypto.Keyring.Keys.PUBLIC_KEY).length());
				this.put(Crypto.Keyring.Keys.ALGORITHM, key.getAlgorithm());
				
				Log.d(Crypto.LOG, this.toString());
			} catch (JSONException e) {}
			catch (IOException e) {}
		}
		
		public KeyServerResponse(PGPPublicKey key) {
			this(key, null, null);
		}
	}
	
	private static String parseKeyserverQuery(String rawdata) {
		String keyblock = null;
		String bodyContents = rawdata.substring(rawdata.indexOf("<body>"), rawdata.indexOf("</html>"));
		
		if(bodyContents.contains(PGP.beginKeyBlock[0])) {
			keyblock = bodyContents.substring(bodyContents.indexOf(PGP.beginKeyBlock[0]), bodyContents.indexOf(PGP.endKeyBlock[0]) + PGP.endKeyBlock[0].length());
		}
		return keyblock;
	}
	
	public static void queryKeyserverByEmail(Activity c, String displayName, String email) {
		try {
			String keyblock = parseKeyserverQuery(HttpUtility.executeHttpGet(PGP.keyserverUrl[0] + email));
			if(keyblock != null) {
				PGPPublicKey key = extractPublicKeyFromBytes(keyblock.getBytes());
				KeyServerResponse keyServerResponse = new KeyServerResponse(key, displayName, email);
				((KeyFoundListener) c).onKeyFound(keyServerResponse);
			}
			
			
		} catch (ClientProtocolException e) {}
		catch (URISyntaxException e) {}
		catch (IOException e) {}
		catch (InterruptedException e) {}
		catch (ExecutionException e) {}
		catch (PGPException e) {}
	}
	
	@SuppressWarnings("unchecked")
	public static PGPPublicKey extractPublicKeyFromBytes(byte[] keyBlock) throws IOException, PGPException {
		PGPPublicKeyRingCollection keyringCol = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(Base64.decode(keyBlock, Base64.DEFAULT))));
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
	
	@SuppressWarnings("unchecked")
	public static boolean installNewKey(Activity c, KeyServerResponse ksr, AddressBookDisplay abd) {
		ContentValues key = new ContentValues();
		Iterator<String> kIt = ksr.keys();
		while(kIt.hasNext()) {
			String k = kIt.next();
			try {
				if(k.equals(Crypto.Keyring.Keys.PUBLIC_KEY))
					key.put(k, ksr.getString(k).getBytes());
				else
					key.put(k, ksr.getString(k));
			} catch(ClassCastException e) {
				try {
					key.put(k, ksr.getLong(k));
				} catch (JSONException e1) {
					e1.printStackTrace();
				} catch(ClassCastException e1) {
					try {
						key.put(k, ksr.getInt(k));
					} catch(JSONException e2) {}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		DatabaseHelper dh = new DatabaseHelper(c);
		SQLiteDatabase db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(c).getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		dh.setTable(db, Tables.Keys.KEYRING);
		db.insert(dh.getTable(), null, key);
		
		if(abd != null) {
			dh.setTable(db, Tables.Keys.TRUSTED_DESTINATIONS);
			ContentValues td = new ContentValues();
			try {
				td.put(TrustedDestination.Keys.DISPLAY_NAME, key.getAsString(PGP.Keys.PGP_DISPLAY_NAME));
				td.put(TrustedDestination.Keys.EMAIL, key.getAsString(PGP.Keys.PGP_EMAIL_ADDRESS));
				td.put(TrustedDestination.Keys.KEYRING_ID, key.getAsLong(PGP.Keys.PGP_KEY_ID));
				td.put(TrustedDestination.Keys.CONTACT_PHOTO, abd.getString(AddressBook.Keys.CONTACT_PHOTO));
				td.put(TrustedDestination.Keys.IS_DELETABLE, abd.getBoolean(TrustedDestination.Keys.IS_DELETABLE));
				
				if(abd.getString(TrustedDestination.Keys.URL) != null)
					td.put(TrustedDestination.Keys.URL, abd.getString(TrustedDestination.Keys.URL));
				
				abd.remove(AddressBook.Keys.CONTACT_PHOTO);
				db.insert(dh.getTable(), null, td);
			} catch(JSONException e){}
		}
		
		db.close();
		dh.close();
		return true;
	}
	
	private static String generatePassword(byte[] baseImage) throws NoSuchAlgorithmException {
		// initialize random bytes
		byte[] randomBytes = new byte[baseImage.length];
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		sr.nextBytes(randomBytes);
		
		// xor by baseImage
		byte[] product = new byte[baseImage.length];
		for(int b = 0; b < baseImage.length; b++) {
			product[b] = (byte) (baseImage[b] ^ randomBytes[b]);
		}
		
		// digest to SHA1 string, voila password.
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		return Base64.encodeToString(md.digest(product), Base64.DEFAULT);
	}
	
	@SuppressWarnings("deprecation")
	public static boolean generateNewKeyFromImage(Activity c) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
		DatabaseHelper dh = new DatabaseHelper(c);
		SQLiteDatabase db = dh.getWritableDatabase(sp.getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		byte[] baseImage;
		
		dh.setTable(db, Tables.Keys.SETUP);
		Cursor b = dh.getValue(db, new String[] {Device.Keys.BASE_IMAGE}, BaseColumns._ID, 1);
		if(b != null && b.moveToFirst()) {
			baseImage = b.getBlob(0);
			b.close();
			
			Security.addProvider(new BouncyCastleProvider());
			KeyPairGenerator kpg;
			
			try {
				String pwd = generatePassword(baseImage);
				kpg = KeyPairGenerator.getInstance("RSA","BC");
				kpg.initialize(4096);
				KeyPair keyPair = kpg.generateKeyPair();
				
				PGPSignatureSubpacketGenerator hashedGen = new PGPSignatureSubpacketGenerator();
				hashedGen.setKeyFlags(true, KeyFlags.ENCRYPT_STORAGE);
				hashedGen.setPreferredCompressionAlgorithms(false, new int[] {
					CompressionAlgorithmTags.ZLIB,
					CompressionAlgorithmTags.ZIP
				});
				hashedGen.setPreferredHashAlgorithms(false, new int[] {
					HashAlgorithmTags.SHA256,
					HashAlgorithmTags.SHA384,
					HashAlgorithmTags.SHA512
				});
				hashedGen.setPreferredSymmetricAlgorithms(false, new int[] {
					SymmetricKeyAlgorithmTags.AES_256,
					SymmetricKeyAlgorithmTags.AES_192,
					SymmetricKeyAlgorithmTags.AES_128,
					SymmetricKeyAlgorithmTags.CAST5,
					SymmetricKeyAlgorithmTags.DES
				});
				
				PGPSecretKey secret = new PGPSecretKey(
						PGPSignature.DEFAULT_CERTIFICATION,
						PublicKeyAlgorithmTags.RSA_GENERAL,
						keyPair.getPublic(),
						keyPair.getPrivate(),
						new Date(),
						"InformaCam OpenPGP Key",
						SymmetricKeyAlgorithmTags.AES_256,
						pwd.toCharArray(),
						hashedGen.generate(),
						null,
						new SecureRandom(),
						"BC");
				
				ContentValues cv = new ContentValues();
				cv.put(Settings.Device.Keys.SECRET_KEY, Base64.encodeToString(secret.getEncoded(), Base64.DEFAULT));
				cv.put(Settings.Device.Keys.AUTH_KEY, pwd);
				cv.put(Settings.Device.Keys.KEYRING_ID, secret.getPublicKey().getKeyID());
				
				db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Integer.toString(1)});
				db.close();
				dh.close();
				
				KeyServerResponse ksr = new KeyServerResponse(
						secret.getPublicKey(), 
						sp.getString(Settings.Keys.DISPLAY_NAME, sp.getString(Settings.Keys.DISPLAY_NAME, "")), 
						sp.getString(Settings.Keys.DISPLAY_NAME, sp.getString(Settings.Keys.DISPLAY_NAME, ""))
				);
				
				installNewKey(c, ksr, null);
				
				return true;
			} catch (NoSuchAlgorithmException e) {
				Log.e(Crypto.LOG, "key error: " + e.toString());
				db.close();
				dh.close();
				return false;
			} catch (NoSuchProviderException e) {
				Log.e(Crypto.LOG, "key error: " + e.toString());
				db.close();
				dh.close();
				return false;
			} catch (PGPException e) {
				Log.e(Crypto.LOG, "key error: " + e.toString());
				db.close();
				dh.close();
				return false;
			} catch (IOException e) {
				Log.e(Crypto.LOG, "key error: " + e.toString());
				db.close();
				dh.close();
				return false;
			}
		}
		
		db.close();
		dh.close();
		return false;
	}
	
	
	public static byte[] applySignature(byte[] data, PGPSecretKey secretKey, PGPPublicKey publicKey, PGPPrivateKey privateKey) {
		int buffSize = 1 <<16;
		BouncyCastleProvider bc = new BouncyCastleProvider();
		
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
			return baos.toByteArray();
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, e.toString());
			e.printStackTrace();
		} catch (PGPException e) {
			Log.e(Crypto.LOG, e.toString());
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			Log.e(Crypto.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(Crypto.LOG, e.toString());
			e.printStackTrace();
		} catch (SignatureException e) {
			Log.e(Crypto.LOG, e.toString());
			e.printStackTrace();
		}
    	return null;
	}
}
