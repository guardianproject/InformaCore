package org.witness.informa.utils;

import java.io.IOException;

import org.json.JSONException;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Media.ShareVector;
import org.witness.informa.utils.InformaConstants.Media.Status;
import org.witness.ssc.video.ShellUtils;

import android.content.Context;

public class MetadataPack {
	public String email, metadata, filepath, clonepath, keyHash;
	public String tdDestination = null;
	public String tmpId, authToken, hash, messageUrl;
	public int mediaType, shareVector, status, retryFlags;
	public long timestampCreated, id;
	private Context c;
	
	public MetadataPack(
			Context c, String clonePath,
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
	
	public void doEncrypt() {
		// TODO: once we have GPG/PGP working...
		// until then, just sign data with the key
		if(tdDestination != null)
			setShareVector(ShareVector.ENCRYPTED_UPLOAD_QUEUE);
		else
			setShareVector(ShareVector.ENCRYPTED_BUT_NOT_UPLOADED);
	}
	
	public void setShareVector(int shareVector) {
		this.shareVector = shareVector;
	}
	
	public void doInject() throws IOException, JSONException {
		if(mediaType == MediaTypes.PHOTO)
			timestampCreated = ImageConstructor.constructImage(this);
		else if(mediaType == MediaTypes.VIDEO)
			timestampCreated = VideoConstructor.constructVideo(c, this, new ShellUtils.ShellCallback() {
				
				@Override
				public void shellOut(char[] msg) {
					
					
				}
			});
	}
}
