package org.witness.informacam.informa.embed;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.App.Informa;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

public class ImageConstructor {
	info.guardianproject.iocipher.File pathToImage;
	info.guardianproject.iocipher.File pathToJ3M;

	java.io.File version;
	InformaCam informaCam;

	String pathToNewImage;
	int destination;
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
	
	public ImageConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, String pathToNewImage, int destination) throws IOException {
		this(media, pathToImage, pathToJ3M, pathToNewImage, destination, null);
	}

	public ImageConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, String pathToNewImage, int destination, ITransportStub connection) throws IOException {
		informaCam = InformaCam.getInstance();

		this.pathToImage = pathToImage;
		this.pathToJ3M = pathToJ3M;
		this.pathToNewImage = pathToNewImage;
		this.destination = destination;
		this.media = media;
		this.connection = connection;

		byte[] metadata = informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER);

		version = new java.io.File(Storage.EXTERNAL_DIR, "version_" + pathToImage.getName());
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		
		ByteArrayInputStream bais = new ByteArrayInputStream(informaCam.ioService.getBytes(pathToImage.getAbsolutePath(), Type.IOCIPHER));
		Bitmap b = BitmapFactory.decodeStream(bais);
		
		try {
			java.io.FileOutputStream fos = new java.io.FileOutputStream(version);
			b.compress(CompressFormat.JPEG, 100, fos);
			fos.flush();
			fos.close();
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		b.recycle();
		bais.close();
		
		int c = constructImage(version.getAbsolutePath(), version.getAbsolutePath(), new String(metadata), metadata.length);

		if(c > 0) {
			finish();
		}
	}

	public void finish() {
		// move back to iocipher
		Log.d(LOG, "FINISHING UP IMAGE CONSTRUCTOR... (destination " + destination + ")");
				
		if(destination == Type.IOCIPHER) {
			final info.guardianproject.iocipher.File newImage = new info.guardianproject.iocipher.File(pathToNewImage);
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					boolean success = informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newImage);
					if(success) {
						if(connection != null) {
							((MetadataEmbededListener) media).onMediaReadyForTransport(connection);
						}
						
						version.delete();
					}
				}
			}).start();
			
			((MetadataEmbededListener) media).onMetadataEmbeded(newImage);
			
						
		} else if(destination == Type.FILE_SYSTEM) {
			java.io.File newImage = new java.io.File(pathToNewImage);
			
			try {
				boolean success = informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newImage, true);
				if(success) {
					version.delete();
				}
			} catch (IOException e) {
				Logger.e(LOG, e);
			}
			
			((MetadataEmbededListener) media).onMetadataEmbeded(newImage);
		}
	}
}
