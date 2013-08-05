package org.witness.informacam.informa.embed;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.ShellUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.utils.ITransportStub;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Ffmpeg;
import org.witness.informacam.utils.Constants.MetadataEmbededListener;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class VideoConstructor {
	static String[] libraryAssets = {"ffmpeg"};
	static java.io.File fileBinDir;

	info.guardianproject.iocipher.File pathToVideo;
	info.guardianproject.iocipher.File pathToJ3M;

	java.io.File clone;
	java.io.File version;
	java.io.File metadata;

	String pathToNewVideo;
	int destination;

	IMedia media;
	ITransportStub connection;

	private final static String LOG = Ffmpeg.LOG;

	public VideoConstructor(Context context) {
		// just push a call to ffmpeg
		fileBinDir =  context.getDir("bin",0);

		if (!new java.io.File(fileBinDir,libraryAssets[0]).exists()) {
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			try {
				bi.installFromRaw();
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
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
		
		metadata = new java.io.File(Storage.EXTERNAL_DIR, "metadata_" + pathToJ3M.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER), metadata, true);

		clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToVideo.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToVideo.getAbsolutePath(), Type.IOCIPHER), clone, true);

		version = new java.io.File(Storage.EXTERNAL_DIR, "version_" + pathToVideo.getName().replace(".mp4", ".mkv"));
		try {
			constructVideo();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}

	private void constructVideo() throws IOException {
		String ffmpegBin = new java.io.File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);

		String[] ffmpegCommand = new String[] {
				ffmpegBin, "-y", "-i", clone.getAbsolutePath(),
				"-attach", metadata.getAbsolutePath(),
				"-metadata:s:2", "mimetype=text/plain",
				"-vcodec", "copy",
				"-acodec", "copy",
				version.getAbsolutePath()
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

			finish();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ArrayList<String> hashVideo(String pathToVideo) {
		/**
		 * Hashes the video frames 
		 * using FFMpeg's RGB hashing function and
		 * hashes audio stream
		 */
		try {
			String ffmpegBin = new java.io.File(fileBinDir,"ffmpeg").getAbsolutePath();
			Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);
			
			InformaCam informaCam = InformaCam.getInstance();
			java.io.File tmpVid = new java.io.File(Storage.EXTERNAL_DIR, System.currentTimeMillis() + "tmp.mp4");
			
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToVideo, Type.IOCIPHER), tmpVid, true);
			
			ArrayList<String[]> ffmpegCommand = new ArrayList<String[]>();
			
			// just check available formats...
			ffmpegCommand.add(new String[] {
					ffmpegBin, "-formats"	
			});
			
			// Does not work:
			/*
			ffmpegCommand.add(new String[] {
					ffmpegBin, "-i", tmpVid.getAbsolutePath(),
					"-f", "rawvideo", "-pix_fmt", 
					"rgb24", "-f", "md5", "-"
			});
			
			ffmpegCommand.add(new String[] {
					ffmpegBin, "-i", tmpVid.getAbsolutePath(),
					"-acodec", "copy", "-f", "md5", "-"
			});
			*/
			
			// TRY:
			
			/*
			ffmpegCommand.add(new String[] {
					ffmpegBin ,"-i", tmpVid.getAbsolutePath(),
					"-f", "framemd5", "-"
			});
			
			// OR:
			ffmpegCommand.add(new String[] {
					ffmpegBin, "-vsync", "0",
					"-i", tmpVid.getAbsolutePath(),
					"-v", "2", "-an", "-vcodec",
					"rawvideo", "-f", "md5", "-"	
			});
			*/
			
			
			final ArrayList<String> hashes = new ArrayList<String>();
			for(String[] cmd : ffmpegCommand) {
				StringBuffer sb = new StringBuffer();
				for(String f: cmd) {
					sb.append(f + " ");
				}
			
				Log.d(LOG, "command to ffmpeg: " + sb.toString());
				
				
				execProcess(cmd, new ShellUtils.ShellCallback () {

					@Override
					public void shellOut(String shellLine) {
						Log.d(LOG, shellLine);
						/*
						if(shellLine.contains("MD5=")) {
							hashes.add(shellLine.split("=")[1]);
						}
						*/
					}

					@Override
					public void processComplete(int exitValue) {

					}
				});
			}
			
			tmpVid.delete();
			return hashes;
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
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
		String ffmpegBin = new java.io.File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);

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
	
	public void finish() {
		// move back to iocipher
		
		InformaCam informaCam = InformaCam.getInstance();
		
		boolean success = false;
		
		if(destination == Type.IOCIPHER) {
			info.guardianproject.iocipher.File newVideo = new info.guardianproject.iocipher.File(pathToNewVideo);
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newVideo);
			
			if(connection != null) {
				((MetadataEmbededListener) media).onMetadataEmbeded(connection);
			} else {
				((MetadataEmbededListener) media).onMetadataEmbeded(newVideo);
			}
			
				
			
			success = true;
			
		} else if(destination == Type.FILE_SYSTEM) {
			java.io.File newVideo = new java.io.File(pathToNewVideo);
			
			try
			{
				success = informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newVideo, true);
				((MetadataEmbededListener) media).onMetadataEmbeded(newVideo);
			}
			catch (IOException ioe)
			{
				Log.e(LOG,"error saving video blob",ioe);
			}
		}

		if (success)
		{
			// TODO: do cleanup, but these should be super-obliterated rather than just deleted.
			version.delete();
			clone.delete();
			metadata.delete();
		}
	}
}
