package org.witness.informacam.crypto;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IKeyStore;
import org.witness.informacam.models.IOrganization;
import org.witness.informacam.models.ISecretKey;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.ICredentials;
import org.witness.informacam.utils.Constants.Models.IUser;
import org.witness.informacam.utils.MediaHasher;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

public class KeyUtility {

	private final static String LOG = App.Crypto.LOG;

	@SuppressWarnings("unchecked")
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

	public static String generatePassword(byte[] baseBytes) throws NoSuchAlgorithmException {
		// initialize random bytes
		byte[] randomBytes = new byte[baseBytes.length];
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		sr.nextBytes(randomBytes);

		// xor by baseImage
		byte[] product = new byte[baseBytes.length];
		for(int b = 0; b < baseBytes.length; b++) {
			product[b] = (byte) (baseBytes[b] ^ randomBytes[b]);
		}

		// digest to SHA1 string, voila password.
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		return Base64.encodeToString(md.digest(product), Base64.DEFAULT);
	}

	@SuppressWarnings("deprecation")
	public static boolean initDevice() {
		int progress = 1;
		Bundle data = new Bundle();
		data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.UI.UPDATE);
		data.putInt(Codes.Keys.UI.PROGRESS, progress);

		String authToken, secretAuthToken, keyStorePassword;
		InformaCam informaCam = InformaCam.getInstance();
		informaCam.update(data);

		try {
			byte[] baseImageBytes = informaCam.ioService.getBytes(informaCam.user.getString(IUser.PATH_TO_BASE_IMAGE), Storage.Type.INTERNAL_STORAGE);

			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			authToken = generatePassword(baseImageBytes);
			secretAuthToken = generatePassword(baseImageBytes);
			keyStorePassword = generatePassword(baseImageBytes);

			informaCam.ioService.initIOCipher(authToken);

			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			informaCam.persistLogin(informaCam.user.getString(IUser.PASSWORD));

			String authTokenBlobBytes = AesUtility.EncryptWithPassword(informaCam.user.getString(IUser.PASSWORD), authToken);
			JSONObject authTokenBlob = (JSONObject) new JSONTokener(authTokenBlobBytes).nextValue();
			authTokenBlob.put(ICredentials.PASSWORD_BLOCK, authTokenBlob.getString("value"));
			authTokenBlob.remove("value");

			if(informaCam.ioService.saveBlob(authTokenBlob.toString().getBytes(), new java.io.File(IUser.CREDENTIALS))) {
				informaCam.user.hasCredentials = true;
				informaCam.user.remove(IUser.PASSWORD);

				progress += 10;
				data.putInt(Codes.Keys.UI.PROGRESS, progress);
				informaCam.update(data);
			}

			if(
					informaCam.ioService.saveBlob(baseImageBytes, new info.guardianproject.iocipher.File(IUser.BASE_IMAGE)) &&
					informaCam.ioService.delete(informaCam.user.getString(IUser.PATH_TO_BASE_IMAGE), Storage.Type.INTERNAL_STORAGE)
					) {
				informaCam.user.remove(IUser.PATH_TO_BASE_IMAGE);

				progress += 10;
				data.putInt(Codes.Keys.UI.PROGRESS, progress);
				informaCam.update(data);
			}

			Security.addProvider(new BouncyCastleProvider());
			KeyPairGenerator kpg;

			kpg = KeyPairGenerator.getInstance("RSA","BC");
			kpg.initialize(4096);
			KeyPair keyPair = kpg.generateKeyPair();

			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

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
			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			PGPSecretKey secret = new PGPSecretKey(
					PGPSignature.DEFAULT_CERTIFICATION,
					PublicKeyAlgorithmTags.RSA_GENERAL,
					keyPair.getPublic(),
					keyPair.getPrivate(),
					new Date(),
					"InformaCam OpenPGP Key",
					SymmetricKeyAlgorithmTags.AES_256,
					secretAuthToken.toCharArray(),
					hashedGen.generate(),
					null,
					new SecureRandom(),
					"BC");

			String pgpKeyFingerprint = new String(Hex.encode(secret.getPublicKey().getFingerprint()));
			informaCam.user.pgpKeyFingerprint = pgpKeyFingerprint;

			ISecretKey secretKeyPackage = new ISecretKey();
			secretKeyPackage.pgpKeyFingerprint = pgpKeyFingerprint;
			secretKeyPackage.secretAuthToken = secretAuthToken;
			secretKeyPackage.secretKey = Base64.encodeToString(secret.getEncoded(), Base64.DEFAULT);

			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ArmoredOutputStream aos = new ArmoredOutputStream(baos);
			aos.write(secret.getPublicKey().getEncoded());
			aos.flush();
			aos.close();
			baos.flush();

			Map<String, byte[]> publicCredentials = new HashMap<String, byte[]>();
			publicCredentials.put(IUser.BASE_IMAGE, baseImageBytes);
			publicCredentials.put(IUser.PUBLIC_KEY, baos.toByteArray());
			baos.close();

			IOUtility.zipFiles(publicCredentials, IUser.PUBLIC_CREDENTIALS, Type.IOCIPHER);

			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			if(informaCam.ioService.saveBlob(new byte[0], new info.guardianproject.iocipher.File(IManifest.KEY_STORE))) {
				// make keystore manifest
				IKeyStore keyStoreManifest = new IKeyStore();
				keyStoreManifest.password = keyStorePassword;
				keyStoreManifest.path = IManifest.KEY_STORE;
				keyStoreManifest.lastModified = System.currentTimeMillis();
				informaCam.saveState(keyStoreManifest);
				Log.d(LOG, "JUST SAVED KEY MANIFEST AND KEY STORE\n" + keyStoreManifest.asJson().toString());
			}
			progress += 10;
			data.putInt(Codes.Keys.UI.PROGRESS, progress);
			informaCam.update(data);

			if(informaCam.ioService.saveBlob(
					secretKeyPackage.asJson().toString().getBytes(), 
					new info.guardianproject.iocipher.File(IUser.SECRET))
					) {
				informaCam.user.alias = informaCam.user.getString(IUser.ALIAS);

				informaCam.user.remove(IUser.AUTH_TOKEN);
				informaCam.user.remove(IUser.PATH_TO_BASE_IMAGE);
				informaCam.user.remove(IUser.ALIAS);
				informaCam.user.hasPrivateKey = true;

				progress += 9;
				data.putInt(Codes.Keys.UI.PROGRESS, progress);
				informaCam.update(data);
			}

			return true;
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (PGPException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;

	}

	@SuppressWarnings({ "deprecation", "unchecked" })
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
		return null;
	}
	
	public static IOrganization installICTD(String rc) {
		return installICTD(rc.getBytes());
	}
	
	public static IOrganization installICTD(String rc, IOrganization organization) {
		return installICTD(rc.getBytes(), organization);
	}
	
	public static IOrganization installICTD(byte[] rc) {
		return installICTD(rc, null);
	}

	public static IOrganization installICTD(byte[] rc, IOrganization organization) {
		InformaCam informaCam = InformaCam.getInstance();
		
		if(organization == null) {
			organization = new IOrganization();
		}
		
		// decrypt
		byte[] rawContent = EncryptionUtility.decrypt(rc);
		if(rawContent == null) {
			return null;
		}

		List<info.guardianproject.iocipher.File> packageFiles = new ArrayList<info.guardianproject.iocipher.File>();
		try {
			for(String filePath : IOUtility.unzipFile(rawContent, MediaHasher.hash(rc, "SHA-1"), Type.IOCIPHER)) {
				packageFiles.add(new info.guardianproject.iocipher.File(filePath));
			}
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		}
		rawContent = null;

		if(packageFiles.size() > 0) {
			String ext = null;
			for(info.guardianproject.iocipher.File file : packageFiles) {
				try {
					ext = file.getName().substring(file.getName().lastIndexOf("."));
				} catch(StringIndexOutOfBoundsException e) {
					continue;
				}

				if(ext.equals(".txt")) {
					try {

						BufferedReader br = new BufferedReader(new InputStreamReader(new info.guardianproject.iocipher.FileInputStream(file)));
						char[] buf = new char[1024];
						int numRead = 0;

						String line;

						while((numRead = br.read(buf)) != -1) {
							line = String.valueOf(buf, 0, numRead);

							String[] lines = line.split(";");
							for(String l : lines) {
								Log.d(App.LOG, l);
								String key = l.split("=")[0];
								String value = l.split("=")[1];

								if(key.equals(Models.IOrganization.REQUEST_URL)) {
									String urlBase = null;
									if(value.indexOf("http://") != -1) {
										urlBase = value.substring(value.indexOf("http://") + 7);
									}
									
									if(value.indexOf("https://") != -1) {
										urlBase = value.substring(value.indexOf("http://") + 8);
									}
									
									if(urlBase != null) {
										String[] urlAndPort = urlBase.split(":");
										if(urlAndPort.length > 1) {
											organization.requestUrl = urlAndPort[0] + "/";
											organization.requestPort = Integer.parseInt(urlAndPort[1]);
										}
									} else {
										urlBase = value + ((value.charAt(value.length() - 1) == '/') ? "" : "/");
										organization.requestUrl = urlBase;
									}
									
									Log.d(LOG, "urlBase: " + urlBase);
									
									
								} if(key.equals(Models.ITransportCredentials.PASSWORD)) {
									organization.transportCredentials.certificatePassword = value;
								}
							}

							buf = new char[1024];
						}

						br.close();
					}  catch(IOException e) {
						Log.e(Storage.LOG, e.toString());
						e.printStackTrace();
					}
				} else if(ext.equals(".p12")) {
					organization.transportCredentials.certificatePath = file.getAbsolutePath();
					Log.d(LOG, "transport credentials: " + organization.transportCredentials.certificatePath);
				} else if(ext.equals(".asc")) {
					// check to see if asc matches org fingerprint
					try {
						byte[] keyBlock = informaCam.ioService.getBytes(file.getAbsolutePath(), Type.IOCIPHER);
						
						String fingerprint = new String(Hex.encode(KeyUtility.extractPublicKeyFromBytes(Base64.encode(keyBlock, Base64.DEFAULT)).getFingerprint()));
						
						// try to match this up with an existing org
						boolean found = false;
						for(IOrganization org :informaCam.installedOrganizations.organizations) {
							if(fingerprint.equals(org.organizationFingerprint)) {
								organization.publicKeyPath = file.getAbsolutePath();
								org.inflate(organization.asJson());
								
								organization = org;
								found = true;
								Log.d(LOG, "importing key with fingerprint: " + fingerprint + "\nwhich should match " + org.organizationFingerprint);
								break;
							}
						}
						
						if(!found) {
							Log.e(LOG, "this fingerprint does not match the organization fingerprint.");
							return null;
						}
						
					} catch (IOException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					} catch (PGPException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
					
					
				}
			}
		}


		return organization;
	}
}
