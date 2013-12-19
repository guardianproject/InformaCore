package org.witness.informacam.informa.embed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.io.IOUtils;
import org.ffmpeg.android.ShellUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Ffmpeg;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class VideoConstructor {
	
	static java.io.File fileBinDir;
	
	String ffmpegBin;
	
	info.guardianproject.iocipher.File pathToVideo;
	info.guardianproject.iocipher.File pathToJ3M;

//	java.io.File clone;
//	java.io.File version;
//	java.io.File metadata;

	String pathToNewVideo;
	int destination;

	IMedia media;
	ITransportStub connection;

	private final static String LOG = Ffmpeg.LOG;

	public VideoConstructor(Context context) {
		
		fileBinDir = new File(context.getFilesDir().getParentFile(),"lib");
		File fileBin = new File(fileBinDir,"libffmpeg.so");
		ffmpegBin = fileBin.getAbsolutePath();
	}
	
	public VideoConstructor(Context context, IMedia media, info.guardianproject.iocipher.File pathToVideo, info.guardianproject.iocipher.File pathToJ3M, String pathToNewVideo, int destination) throws IOException {
		this(context, media, pathToVideo, pathToJ3M, pathToNewVideo, destination, null);
	}

	public VideoConstructor(Context context, IMedia media, info.guardianproject.iocipher.File pathToVideo, info.guardianproject.iocipher.File pathToJ3M, String pathToNewVideo, int destination, ITransportStub connection) throws IOException {
		this(context);
		
		this.pathToVideo = pathToVideo;
		this.pathToJ3M = pathToJ3M;
		this.media = media;
		this.pathToNewVideo = pathToNewVideo;
		this.destination = destination;
		this.connection = connection;

		InformaCam informaCam = InformaCam.getInstance();
		
		java.io.File metadata = new java.io.File(Storage.EXTERNAL_DIR, "metadata_" + pathToJ3M.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER), metadata, true);

		java.io.File clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToVideo.getName());

		java.io.File version = new java.io.File(Storage.EXTERNAL_DIR, "version_" + pathToVideo.getName().replace(".mp4", ".mkv"));
		try {
			InputStream is = informaCam.ioService.getStream(pathToVideo.getAbsolutePath(), Type.IOCIPHER);			
			java.io.FileOutputStream fos = new java.io.FileOutputStream(clone);			
			IOUtils.copy(is,fos);			
			fos.flush();
			fos.close();

			constructVideo(metadata, clone, version);
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	private void constructVideo(java.io.File fileMetadata, java.io.File fileVideo, java.io.File fileOutput) throws IOException {

		String[] ffmpegCommand = new String[] {
				ffmpegBin, "-y", "-i", fileVideo.getAbsolutePath(),
				"-attach", fileMetadata.getAbsolutePath(),
				"-metadata:s:2", "mimetype=text/plain",
				"-vcodec", "copy",
				"-acodec", "copy",
				fileOutput.getAbsolutePath()
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

			Log.d(LOG, "ffmpeg process completed");

			finish(fileMetadata, fileVideo, fileOutput);
		} catch (Exception e) {
			Logger.e(LOG, e);
		}
	}

	String newHash = null;
	
	
	public String hashMedia(int fileType, String pathToMedia, String extension) {
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
				
				//informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToMedia, Type.IOCIPHER), tmpMedia, true);
			}
			else if (fileType == Type.FILE_SYSTEM)
			{
				tmpMedia = new java.io.File(pathToMedia);
			}
			
			String[] cmdHash = new String[] {
					ffmpegBin, "-i", tmpMedia.getCanonicalPath(),
					"-acodec", "copy", "-f", "md5", "-"
			};				
			
			if (extension.equalsIgnoreCase("jpg"))
			{				
				cmdHash = new String[] {
						ffmpegBin, 
						"-i", tmpMedia.getCanonicalPath(),
						"-vcodec", "copy",
						"-an",
						"-f", "md5", 
						"-"
				};			
			}
			
			
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


			if (process != null) {
				process.destroy();   
			}
		} catch (IOException e) {
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
	
	public void finish(final java.io.File fileMetadata, final java.io.File fileVideo, final java.io.File fileOutput) {
		// move back to iocipher
		
		final InformaCam informaCam = InformaCam.getInstance();
				
		if(destination == Type.IOCIPHER) {
			final info.guardianproject.iocipher.File newVideo = new info.guardianproject.iocipher.File(pathToNewVideo);
			
			try
			{
				boolean success = informaCam.ioService.saveBlob(new FileInputStream(fileOutput), newVideo);			
				
				if(success) {
					if(connection != null) {
						((MetadataEmbededListener) media).onMediaReadyForTransport(connection);
					}
					
					fileOutput.delete();
					fileVideo.delete();
					fileMetadata.delete();
				}
			}
			catch (IOException fnfe)
			{
				Log.e(LOG,"Could not export video file",fnfe);
			}
			
			((MetadataEmbededListener) media).onMetadataEmbeded(newVideo);	
			
		} else if(destination == Type.FILE_SYSTEM) {
			java.io.File newVideo = new java.io.File(pathToNewVideo);
			
			try {
				boolean success = informaCam.ioService.saveBlob(new FileInputStream(fileOutput), newVideo, true);
				if(success) {
					fileOutput.delete();
					fileVideo.delete();
					fileMetadata.delete();
				}
			} catch(IOException e) {
				Logger.e(LOG, e);
			}
			
			((MetadataEmbededListener) media).onMetadataEmbeded(newVideo);
		}
	}
}
