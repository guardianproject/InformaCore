package org.witness.informacam.informa.embed;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.media.ISubmission;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

public class ImageConstructor {
	ISubmission pendingConnection;
	info.guardianproject.iocipher.File pathToImage;
	info.guardianproject.iocipher.File pathToJ3M;
	
	java.io.File clone, version;
	InformaCam informaCam;
	
	static {
		System.loadLibrary("JpegRedaction");
	}
	
	public static native int constructImage(
			String originalImageFilename, 
			String informaImageFilename, 
			String metadataObjectString, 
			int metadataLength);
	
	public ImageConstructor(info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, ISubmission pendingConnection) {
		informaCam = InformaCam.getInstance();
		
		this.pathToImage = pathToImage;
		this.pathToJ3M = pathToJ3M;
		this.pendingConnection = pendingConnection;
		
		byte[] metadata = informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER);
		
		clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToImage.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToImage.getAbsolutePath(), Type.IOCIPHER), clone);
		
		version = new java.io.File(Storage.EXTERNAL_DIR, pathToImage.getName());
		int c = constructImage(clone.getAbsolutePath(), version.getAbsolutePath(), new String(metadata), metadata.length);
		if(c > 0) {
			IPendingConnections pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());
			
			ISubmission submission = (ISubmission) pendingConnections.queue.get(pendingConnections.queue.indexOf(pendingConnection));
			if(submission != null) {
				submission.isHeld = false;
				informaCam.saveState(pendingConnections);
			}
		}
	}
}
