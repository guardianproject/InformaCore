package org.witness.informacam.informa.embed;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.media.IMedia;
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

	java.io.File clone;
	java.io.File version;
	InformaCam informaCam;

	String pathToNewImage;
	int destination;
	IMedia media;
	IConnection connection;
	
	private final static String LOG = Informa.LOG;

	static {
		System.loadLibrary("JpegRedaction");
	}

	public static native int constructImage(
			String clonePath, 
			String versionPath, 
			String j3mString, 
			int j3mStringLength);
	
	public ImageConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, String pathToNewImage, int destination) {
		this(media, pathToImage, pathToJ3M, pathToNewImage, destination, null);
	}

	public ImageConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, String pathToNewImage, int destination, IConnection connection) {
		informaCam = InformaCam.getInstance();

		this.pathToImage = pathToImage;
		this.pathToJ3M = pathToJ3M;
		this.pathToNewImage = pathToNewImage;
		this.destination = destination;
		this.media = media;
		this.connection = connection;

		byte[] metadata = informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER);

		clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToImage.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToImage.getAbsolutePath(), Type.IOCIPHER), clone, true);

		version = new java.io.File(Storage.EXTERNAL_DIR, "version_" + pathToImage.getName());
		Bitmap b = BitmapFactory.decodeFile(clone.getAbsolutePath());
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
		
		int c = constructImage(version.getAbsolutePath(), version.getAbsolutePath(), new String(metadata), metadata.length);

		if(c > 0) {
			finish();
		}
	}

	public void finish() {
		// move back to iocipher
		if(destination == Type.IOCIPHER) {
			info.guardianproject.iocipher.File newImage = new info.guardianproject.iocipher.File(pathToNewImage);
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newImage);
			
			ISubmission submission = new ISubmission();	// downcast the connection to submission
			submission.inflate(connection.asJson());
			
			submission.Set(newImage);
			media.onMetadataEmbeded(newImage);
			
		} else if(destination == Type.FILE_SYSTEM) {
			java.io.File newImage = new java.io.File(pathToNewImage);
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newImage, true);
			media.onMetadataEmbeded(newImage);
		}
		
		// TODO: do cleanup, but these should be super-obliterated rather than just deleted.
		clone.delete();
		version.delete();
		
	}
}
