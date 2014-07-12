package org.witness.informacam.informa.embed;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.witness.informacam.Debug;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IAsset;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App.Informa;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class ImageConstructor {
	InformaCam informaCam;

	IAsset destinationAsset, sourceAsset;
	IMedia media;
	ITransportStub connection;
	
	private final static String LOG = Informa.LOG;

	static {
		System.loadLibrary("JpegRedaction");
	}

	public static native int constructImage(
			String clonePath, 
			String versionPath, 
			String j3mString, 
			int j3mStringLength);
	
	public ImageConstructor(IMedia media, IAsset destinationAsset, ITransportStub connection) throws IOException {
		informaCam = InformaCam.getInstance();

		this.media = media;
		this.destinationAsset = destinationAsset;
		this.connection = connection;
		
		sourceAsset = this.media.dcimEntry.fileAsset;		
		java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
		if(!publicRoot.exists()) {
			publicRoot.mkdir();
		}
		
		boolean intendedForIOCipher = false;
		if(sourceAsset.source == Type.IOCIPHER) {
			// If the assets were in IOCIPHER, we have to save them to local storage.
			sourceAsset.copy(Type.IOCIPHER, Type.FILE_SYSTEM, media.rootFolder);			
			
			// this means we also have to save the resulting media to public
			// (and copy to iocipher later)
			this.destinationAsset.copy(Type.IOCIPHER, Type.FILE_SYSTEM, media.rootFolder, false);
			intendedForIOCipher = true;
		}
		else if (sourceAsset.source == Type.FILE_SYSTEM)
		{
			java.io.File fileDest = new java.io.File(destinationAsset.path);
			fileDest.delete();			
			IOUtils.copy(new java.io.FileInputStream(sourceAsset.path),new java.io.FileOutputStream(fileDest));
			intendedForIOCipher = false;
		}

		String metadata = new String(informaCam.ioService.getBytes(this.media.getAsset(Models.IMedia.Assets.J3M)));
		
		try {
			int c = constructImage(sourceAsset.path, this.destinationAsset.path, metadata, metadata.length());			
			
			if(c > 0) {
				finish(intendedForIOCipher);
			}
		}
		catch (Exception e)
		{
			Log.e(LOG,"error unable to export image",e);
		}
	}

	public IAsset finish(boolean intentedForIOCipher) {
		//Log.d(LOG, "FINISHING UP IMAGE CONSTRUCTOR... (destination " + destinationAsset.path + ")");
		
		int storageType = Storage.Type.FILE_SYSTEM;
		
		// if this was in encrypted space, delete temp files
		if(intentedForIOCipher) {
			try {
				destinationAsset.copy(Type.FILE_SYSTEM, Type.IOCIPHER, media.rootFolder);
				storageType = Storage.Type.IOCIPHER;
			} catch (IOException e) {
				Logger.e(LOG, e);
			}
			
			java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
			InformaCam.getInstance().ioService.clear(publicRoot.getAbsolutePath(), Type.FILE_SYSTEM);
		}
		
		if(connection != null) {
			connection.setAsset(destinationAsset, "image/jpeg", storageType);
			((MetadataEmbededListener) media).onMediaReadyForTransport(connection);
		}
		
		((MetadataEmbededListener) media).onMetadataEmbeded(destinationAsset);
		
		return destinationAsset;
	}
}
