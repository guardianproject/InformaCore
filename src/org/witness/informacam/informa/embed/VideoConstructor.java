package org.witness.informacam.informa.embed;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.ShellUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.utils.Constants.Ffmpeg;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

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

	InformaCam informaCam;
	IMedia media;
	IConnection connection;

	private final static String LOG = Ffmpeg.LOG;

	public VideoConstructor() {
		// just push a call to ffmpeg
		fileBinDir = informaCam.a.getDir("bin",0);

		if (!new java.io.File(fileBinDir,libraryAssets[0]).exists()) {
			BinaryInstaller bi = new BinaryInstaller(informaCam.a.getApplicationContext(),fileBinDir);
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
	
	public VideoConstructor(IMedia media, info.guardianproject.iocipher.File pathToVideo, info.guardianproject.iocipher.File pathToJ3M, String pathToNewVideo, int destination) {
		this(media, pathToVideo, pathToJ3M, pathToNewVideo, destination, null);
	}

	public VideoConstructor(IMedia media, info.guardianproject.iocipher.File pathToVideo, info.guardianproject.iocipher.File pathToJ3M, String pathToNewVideo, int destination, IConnection connection) {
		this.pathToVideo = pathToVideo;
		this.pathToJ3M = pathToJ3M;
		this.media = media;
		this.pathToNewVideo = pathToNewVideo;
		this.destination = destination;
		this.connection = connection;

		informaCam = InformaCam.getInstance();

		fileBinDir = informaCam.a.getDir("bin",0);


		if (!new java.io.File(fileBinDir,libraryAssets[0]).exists()) {
			BinaryInstaller bi = new BinaryInstaller(informaCam.a.getApplicationContext(),fileBinDir);
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
		if(destination == Type.IOCIPHER) {
			info.guardianproject.iocipher.File newVideo = new info.guardianproject.iocipher.File(pathToNewVideo);
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newVideo);
			
			if(connection != null) {
				ISubmission submission = new ISubmission();	// downcast the connection to submission
				submission.inflate(connection.asJson());
			
				submission.Set(newVideo);
			}
			
			media.onMetadataEmbeded(newVideo);			
		} else if(destination == Type.FILE_SYSTEM) {
			java.io.File newVideo = new java.io.File(pathToNewVideo);
			informaCam.ioService.saveBlob(informaCam.ioService.getBytes(version.getAbsolutePath(), Type.FILE_SYSTEM), newVideo, true);
			media.onMetadataEmbeded(newVideo);
		}

		// TODO: do cleanup, but these should be super-obliterated rather than just deleted.
		version.delete();
		clone.delete();
		metadata.delete();
		
	}
}
