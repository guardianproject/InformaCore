package org.witness.informacam.informa.embed;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IPendingConnections;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

public class ImageConstructor {
	info.guardianproject.iocipher.File pathToImage;
	info.guardianproject.iocipher.File pathToJ3M;

	java.io.File clone;
	java.io.File version;
	InformaCam informaCam;
	IMedia media;

	static {
		System.loadLibrary("JpegRedaction");
	}

	public static native int constructImage(
			String clonePath, 
			String versionPath, 
			String j3mString, 
			int j3mStringLength);

	public ImageConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M) {
		informaCam = InformaCam.getInstance();

		this.media = media;
		this.pathToImage = pathToImage;
		this.pathToJ3M = pathToJ3M;

		byte[] metadata = informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER);

		clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToImage.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToImage.getAbsolutePath(), Type.IOCIPHER), clone);

		version = new java.io.File(Storage.EXTERNAL_DIR, pathToImage.getName());
		int c = constructImage(clone.getAbsolutePath(), version.getAbsolutePath(), new String(metadata), metadata.length);

		if(c > 0) {
			finish();
			media.onMetadataEmbeded(pathToImage);
		}


	}

	public void finish() {
		// move back to iocipher
		if(informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), pathToImage)) {
			// TODO: do cleanup, but these should be super-obliterated rather than just deleted.
			clone.delete();
			version.delete();
		}
	}
}
