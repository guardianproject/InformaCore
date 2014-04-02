package org.witness.informacam.informa.embed;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
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
	java.io.File version;
	InformaCam informaCam;

	IAsset destinationAsset;
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
		
		if(this.media.dcimEntry.fileAsset.source == Type.IOCIPHER) {
			java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
			if(!publicRoot.exists()) {
				publicRoot.mkdir();
			}
		}

		byte[] metadata = informaCam.ioService.getBytes(this.media.getAsset(Models.IMedia.Assets.J3M));
		version = new java.io.File(IOUtility.buildPublicPath(new String[] { this.media.rootFolder, "version_" + media.dcimEntry.fileAsset.name }));
		
		try {
			InputStream is = informaCam.ioService.getStream(media.dcimEntry.fileAsset);			
			java.io.FileOutputStream fos = new java.io.FileOutputStream(version);			
			IOUtils.copy(is,fos);			
			fos.flush();
			fos.close();
		} catch (IOException e) {
			Log.e(LOG, "error copying file to output",e);
		}

		try
		{
			int c = constructImage(version.getAbsolutePath(), version.getAbsolutePath(), new String(metadata), metadata.length);

		//	String hashAfter = MediaHasher.getJpegHash(new FileInputStream(version.getAbsolutePath()));			
		//	Log.d(LOG,"export media hash:" + hashAfter);
			
			
			if(c > 0) {
				finish();
			}
		}
		catch (Exception e)
		{
			Log.e(LOG,"error unable to export image",e);
		}
	}

	public void finish() throws IOException {
		Log.d(LOG, "FINISHING UP IMAGE CONSTRUCTOR... (destination " + destinationAsset.path + ")");
		
		boolean success = informaCam.ioService.saveBlob(informaCam.ioService.getStream(version.getAbsolutePath(), Type.FILE_SYSTEM), destinationAsset);
		if(success) {
			if(connection != null) {
				((MetadataEmbededListener) media).onMediaReadyForTransport(connection);
			}
		}
		
		// if this was in encrypted space, delete temp files
		if(media.dcimEntry.fileAsset.source == Type.IOCIPHER) {
			java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
			InformaCam.getInstance().ioService.clear(publicRoot.getAbsolutePath(), Type.FILE_SYSTEM);
		}
		
		((MetadataEmbededListener) media).onMetadataEmbeded(destinationAsset);
	}
}
