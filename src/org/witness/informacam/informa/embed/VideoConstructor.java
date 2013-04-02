package org.witness.informacam.informa.embed;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.ShellUtils;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IPendingConnections;
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

	info.guardianproject.iocipher.File pathToImage;
	info.guardianproject.iocipher.File pathToJ3M;
	ISubmission pendingConnection;

	java.io.File clone;
	java.io.File version;
	java.io.File metadata;

	InformaCam informaCam;
	IMedia media;

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

	public VideoConstructor(IMedia media, info.guardianproject.iocipher.File pathToImage, info.guardianproject.iocipher.File pathToJ3M, ISubmission pendingConnection) {
		this.pathToImage = pathToImage;
		this.pathToJ3M = pathToJ3M;
		this.pendingConnection = pendingConnection;
		this.media = media;

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
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToJ3M.getAbsolutePath(), Type.IOCIPHER), metadata);

		clone = new java.io.File(Storage.EXTERNAL_DIR, "clone_" + pathToImage.getName());
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(pathToImage.getAbsolutePath(), Type.IOCIPHER), clone);

		version = new java.io.File(Storage.EXTERNAL_DIR, pathToImage.getName());
		try {
			constructVideo();
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
					if(pendingConnection != null) {
						IPendingConnections pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());

						ISubmission submission = (ISubmission) pendingConnections.queue.get(pendingConnections.queue.indexOf(pendingConnection));
						if(submission != null) {
							submission.isHeld = false;
							informaCam.saveState(pendingConnections);
						}
					}

					// move back to iocipher
					// clean up
					
					// ping back
					media.onMetadataEmbeded(version);
				}
			});
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
		final java.io.File tmp = new java.io.File(Storage.EXTERNAL_DIR, "bmp_" + System.currentTimeMillis());
		String ffmpegBin = new java.io.File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);

		String[] ffmpegCommand = new String[] {
				ffmpegBin, "-t", "1", 
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
					Log.d(LOG, "PROCESS COMPLETE");
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		return BitmapFactory.decodeFile(tmp.getAbsolutePath());
	}
	
	public boolean finish() {
		// move back to iocipher
		// do cleanup
		return true;
	}


}
