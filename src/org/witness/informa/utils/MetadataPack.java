package org.witness.informa.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.json.JSONException;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Media.ShareVector;
import org.witness.informa.utils.InformaConstants.Media.Status;
import org.witness.ssc.video.ShellUtils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

public class MetadataPack {
	public String email, metadata, filepath, clonepath, keyHash;
	public String tdDestination = null;
	public String tmpId, authToken, hash, messageUrl;
	public int mediaType, shareVector, status, retryFlags;
	public long timestampCreated, id;
	public byte[] encryptionKey;
	
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
	
	public void doEncrypt() {
		try {
			PGPPublicKey encKey = null;
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
			
			if(encKey == null)
				throw new IllegalArgumentException("uch i cannot find a key here");
			else
				Log.d(InformaConstants.TAG, "hey guess we reconstructed the key");
			
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG, "key is null dummy, you can't encrypt this. " + e.toString());
		} catch (IOException e) {
			Log.e(InformaConstants.TAG, "encryption troubles: " + e.toString());
			e.printStackTrace();
		} catch (PGPException e) {
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
