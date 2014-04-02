package org.witness.informacam.informa.embed;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.io.IOUtils;
import org.ffmpeg.android.ShellUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IAsset;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Ffmpeg;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;
import org.witness.informacam.utils.Constants.Models;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class VideoConstructor {
	
	static java.io.File fileBinDir;
	
	String ffmpegBin;
	
	IMedia media;
	IAsset destinationAsset, sourceAsset, metadata;
	ITransportStub connection;

	private final static String LOG = Ffmpeg.LOG;

	public VideoConstructor(Context context) {
		
		fileBinDir = new File(context.getFilesDir().getParentFile(),"lib");
		File fileBin = new File(fileBinDir,"libffmpeg.so");
		ffmpegBin = fileBin.getAbsolutePath();
	}
	
	public VideoConstructor(Context context, IMedia media, IAsset destinationAsset, ITransportStub connection) throws IOException {
		this(context);
		
		this.media = media;
		this.destinationAsset = destinationAsset;
		this.sourceAsset = this.media.dcimEntry.fileAsset;
		this.connection = connection;

		InformaCam informaCam = InformaCam.getInstance();
		
		java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
		if(!publicRoot.exists()) {
			publicRoot.mkdir();
		}
		
		metadata = media.getAsset(Models.IMedia.Assets.J3M);
		
		if(this.media.dcimEntry.fileAsset.source == Type.IOCIPHER) {
			byte[] metadataBytes = informaCam.ioService.getBytes(metadata);
			metadata.source = Type.FILE_SYSTEM;
			metadata.path = IOUtility.buildPublicPath(new String[] { media.rootFolder, metadata.name });
			informaCam.ioService.saveBlob(metadataBytes, metadata);
			
			sourceAsset.path = IOUtility.buildPublicPath(new String[] { media.rootFolder, sourceAsset.name });
			sourceAsset.source = Type.FILE_SYSTEM;
			informaCam.ioService.saveBlob(informaCam.ioService.getStream(media.dcimEntry.fileAsset), sourceAsset);
		}
		
		constructVideo();
	}

	private void constructVideo() throws IOException {

		String[] ffmpegCommand = new String[] {
				ffmpegBin, "-y", "-i", sourceAsset.path,
				"-attach", metadata.path,
				"-metadata:s:2", "mimetype=text/plain",
				"-vcodec", "copy",
				"-acodec", "copy",
				destinationAsset.path
		};

		StringBuffer sb = new StringBuffer();
		for(String f: ffmpegCommand) {
			sb.append(f + " ");
		}
		Log.d(LOG, "command to ffmpeg: " + sb.toString());

		try {
			execProcess(ffmpegCommand, new ShellUtils.ShellCallback () {

				@Override
				public void shellOut(String shellLine) {
					Log.d(LOG, shellLine);
				}

				@Override
				public void processComplete(int exitValue) {
					Log.d(LOG, "ffmpeg process completed");
					
					// if this was in encrypted space, delete temp files
					if(media.dcimEntry.fileAsset.source == Type.IOCIPHER) {
						java.io.File publicRoot = new java.io.File(IOUtility.buildPublicPath(new String[] { media.rootFolder }));
						InformaCam.getInstance().ioService.clear(publicRoot.getAbsolutePath(), Type.FILE_SYSTEM);
					}
					
					if(connection != null) {
						((MetadataEmbededListener) media).onMediaReadyForTransport(connection);
					}
					
					((MetadataEmbededListener) media).onMetadataEmbeded(destinationAsset);
				}
			});


		} catch (Exception e) {
			Logger.e(LOG, e);
		}
	}

	String newHash = null;
	
	
	public String hashVideo(String pathToMedia, int fileType, String extension) {
		/**
		 * Hashes the video frames 
		 * using FFMpeg's RGB hashing function and
		 * hashes audio stream
		 */
		
		try
		{
			InformaCam informaCam = InformaCam.getInstance();
			java.io.File tmpMedia = null;
			
			if (fileType == Type.IOCIPHER)
			{
				tmpMedia = new java.io.File(Storage.EXTERNAL_DIR, System.currentTimeMillis() + "tmp." + extension);		
				
				InputStream is = informaCam.ioService.getStream(pathToMedia, Type.IOCIPHER);			
				java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpMedia);			
				IOUtils.copy(is,fos);			
				fos.flush();
				fos.close();
			}
			else if (fileType == Type.FILE_SYSTEM)
			{
				tmpMedia = new java.io.File(pathToMedia);
			}
			
			String[] cmdHash = new String[] {
					ffmpegBin, "-i", tmpMedia.getCanonicalPath(),
					"-vcodec", "copy", "-an", "-f", "md5", "-"
			};				
			
			execProcess(cmdHash, new ShellUtils.ShellCallback () {

				@Override
				public void shellOut(String shellLine) {
					
					if(shellLine.contains("MD5=")) {
						String hashLine = shellLine.split("=")[1];
						newHash = hashLine.split(" ")[0].trim();
					}
					
				}

				@Override
				public void processComplete(int exitValue) {
					
						if (newHash == null)
							newHash = "unknown";
				}
			});
			
			while (newHash == null)
			{
				try { Thread.sleep(500); } 
				catch (Exception e){}
			}
			
			if (fileType == Type.IOCIPHER)
				tmpMedia.delete();
			
			return newHash;
			
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	private static void execProcess(String[] cmds, ShellUtils.ShellCallback sc) {
		ProcessBuilder pb = new ProcessBuilder(cmds);
		pb.redirectErrorStream(true);
		Process process;
		try {
			process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;

			while ((line = reader.readLine()) != null)
			{
				if (sc != null) {
					Log.d(LOG, line);
					sc.shellOut(line);
				}
			}

			sc.processComplete(process.waitFor());
			
			process.destroy();   
			
		} catch (Exception e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}      

	}
	
	public Bitmap getAFrame(java.io.File source, int[] dims) throws IOException {
		return getAFrame(source, dims, 1);
	}

	public Bitmap getAFrame(java.io.File source, int[] dims, int frame) throws IOException {		
		final java.io.File tmp = new java.io.File(Storage.EXTERNAL_DIR, "bmp_" + System.currentTimeMillis());

		String[] ffmpegCommand = new String[] {
				ffmpegBin, "-t", String.valueOf(frame), 
				"-i", source.getAbsolutePath(),
				"-ss", "0.5",
				"-s", (dims[0] + "x" + dims[1]),
				"-f", "image2",
				tmp.getAbsolutePath()
		};

		StringBuffer sb = new StringBuffer();
		for(String f: ffmpegCommand) {
			sb.append(f + " ");
		}
		Log.d(LOG, "command to ffmpeg: " + sb.toString());

		try {
			execProcess(ffmpegCommand, new ShellUtils.ShellCallback () {

				@Override
				public void shellOut(String shellLine) {
					Log.d(LOG, shellLine);
				}

				@Override
				public void processComplete(int exitValue) {
					
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return BitmapFactory.decodeFile(tmp.getAbsolutePath());
	}
}
