package org.witness.informa.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.Date;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.json.JSONException;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Media.ShareVector;
import org.witness.informa.utils.InformaConstants.Media.Status;
import org.witness.ssc.video.ShellUtils;

import android.util.Base64;
import android.util.Log;

public class MetadataPack {
	public String email, metadata, filepath, clonepath, keyHash;
	public String tdDestination = null;
	public String encryptedMetadata = null;
	public String tmpId, authToken, hash, messageUrl;
	public int mediaType, shareVector, status, retryFlags;
	public long timestampCreated, id;
	public byte[] encryptionKey;
	
	private BouncyCastleProvider bc;
	
	public MetadataPack(
			String clonePath,
			long id, String email, String metadata, String filepath, 
			String hash, int mediaType, String keyHash) {
		this.id = id;
		this.email = email;
		this.metadata = metadata;
		this.filepath = filepath;
		this.clonepath = clonePath;
		this.hash = hash;
		this.mediaType = mediaType;
		this.keyHash = keyHash;
		this.shareVector = ShareVector.UNENCRYPTED_NOT_UPLOADED;
		this.status = Status.NEVER_SCHEDULED_FOR_UPLOAD;
		this.retryFlags = 0;		
	}
	
	public void setTDDestination(String tdDestination) {
		this.tdDestination = tdDestination;
		this.status = Status.UPLOADING;
	}
	
	public void setEncryptionKey(byte[] key) {
		this.encryptionKey = key;
	}
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void doEncrypt() {
		bc = new BouncyCastleProvider();
		try {
			PGPPublicKey encKey = null;
			try {
				PGPPublicKeyRingCollection keyRingCol = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(this.encryptionKey)));
				Iterator<PGPPublicKeyRing> rIt = keyRingCol.getKeyRings();
				while(rIt.hasNext()) {
					PGPPublicKeyRing keyRing = (PGPPublicKeyRing) rIt.next();
					Iterator<PGPPublicKey> kIt = keyRing.getPublicKeys();
					
					while(kIt.hasNext()) {
						PGPPublicKey key = (PGPPublicKey) kIt.next();
						if(key.isEncryptionKey())
							encKey = key;
					}
				}
			} catch(PGPException e) {
				// assumably, key found where key ring should be?
				PGPObjectFactory objFact = new PGPObjectFactory(PGPUtil.getDecoderStream(new ByteArrayInputStream(this.encryptionKey)));
				Object obj;
				
				while((obj = objFact.nextObject()) != null) {
					if(obj instanceof PGPPublicKey) {
						encKey = (PGPPublicKey) obj;
						continue;
					}
				}
			}
			
			if(encKey == null) {
				Log.e(InformaConstants.TAG, "bad reference for: " + this.email);
			} else {
				try {
					Log.e(InformaConstants.TAG, "good reference for: " + this.email);
					Log.d(InformaConstants.TAG, "hey guess we reconstructed the key!");
					
					// TODO: ENCRYPTION HAPPENS HERE
					int bufferSize = 1 << 16;
					Security.addProvider(new BouncyCastleProvider());
					
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					OutputStream aos =  new ArmoredOutputStream(baos);
					
					PGPEncryptedDataGenerator edg = new PGPEncryptedDataGenerator(
							PGPEncryptedData.AES_256, true, new SecureRandom(), bc);
					edg.addMethod(encKey);
					OutputStream encOs = edg.open(aos, new byte[bufferSize]);
					
					PGPCompressedDataGenerator compressedData = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
					OutputStream compOs = compressedData.open(encOs);
					
					PGPLiteralDataGenerator literalData = new PGPLiteralDataGenerator();
					OutputStream litOs = literalData.open(compOs, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, new Date(System.currentTimeMillis()), new byte[bufferSize]);
					
					InputStream is = new ByteArrayInputStream(this.metadata.getBytes());
					byte[] buf = new byte[bufferSize];
					int len;
					while((len = is.read(buf)) > 0)
						litOs.write(buf, 0, len);
					
					litOs.close();
					literalData.close();
					
					compOs.close();
					compressedData.close();
					
					encOs.close();
					edg.close();
					
					is.close();
					this.encryptedMetadata = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
					Log.e(InformaConstants.TAG, "OH HO!\n" + new String(baos.toByteArray()));
					
					aos.close();
					baos.close();
				} catch(PGPException e) {
					Log.e(InformaConstants.TAG, "bad reference for: " + this.email);
					Log.e(InformaConstants.TAG, "encryption troubles: " + e.toString());
					e.printStackTrace();
				}
			}
			
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG, "bad reference for: " + this.email);
			Log.e(InformaConstants.TAG, "key is null dummy, you can't encrypt this. " + e.toString());
		} catch (IOException e) {
			Log.e(InformaConstants.TAG, "bad reference for: " + this.email);
			Log.e(InformaConstants.TAG, "encryption troubles: " + e.toString());
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			Log.e(InformaConstants.TAG, "bad reference for: " + this.email);
			Log.e(InformaConstants.TAG, "encryption troubles: " + e.toString());
			e.printStackTrace();
		}
	}
	
	public void setShareVector(int shareVector) {
		this.shareVector = shareVector;
	}
	
	public void doInject() throws IOException, JSONException {
		if(mediaType == MediaTypes.PHOTO)
			timestampCreated = ImageConstructor.constructImage(this);
		else if(mediaType == MediaTypes.VIDEO) {
			timestampCreated = VideoConstructor.getVideoConstructor().constructVideo(this, new ShellUtils.ShellCallback() {
				
				@Override
				public void shellOut(char[] msg) {
					
					
				}
			});
		}
	}
}
