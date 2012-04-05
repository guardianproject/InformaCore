package org.witness.informa.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.Informa.Video;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Ass;
import org.witness.informa.utils.InformaConstants.Keys.CaptureEvent;
import org.witness.informa.utils.InformaConstants.Keys.Data;
import org.witness.informa.utils.InformaConstants.Keys.Events;
import org.witness.informa.utils.io.BinaryInstaller;
import org.witness.informa.utils.io.ShellUtils;
import org.witness.informa.utils.io.ShellUtils.ShellCallback;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.video.ObscureRegion;

import android.content.Context;
import android.util.Log;

public class VideoConstructor {

	String[] libraryAssets = {"ffmpeg"};
	File fileBinDir;
	Context context;

	public VideoConstructor(Context _context) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);

		if (!new File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}
	}
	
	private void execProcess(String[] cmds, ShellCallback sc) throws Exception {		
        
		
			ProcessBuilder pb = new ProcessBuilder(cmds);
			pb.redirectErrorStream(true);
	    	Process process = pb.start();      
	    	
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	
			String line;
			
			while ((line = reader.readLine()) != null)
			{
				if (sc != null)
					sc.shellOut(line.toCharArray());
			}

			
		    if (process != null) {
		    	process.destroy();        
		    }

	}
	
	public class FFMPEGArg
	{
		String key;
		String value;
		
		public static final String ARG_VIDEOCODEC = "vcodec";
		public static final String ARG_VERBOSITY = "v";
		public static final String ARG_FILE_INPUT = "i";
		public static final String ARG_SIZE = "-s";
		public static final String ARG_FRAMERATE = "-r";
		public static final String ARG_FORMAT = "-f";
		
	}
	
	public void writeMetadata(Video source) {
		Log.d(InformaConstants.VIDEO_LOG, source.getMetadataPackage().toString());
		
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		try {
			Runtime.getRuntime().exec("chmod 700 " +ffmpegBin);
		} catch (IOException e) {
			Log.e(InformaConstants.VIDEO_LOG, e.toString());
		}
		
		try {
			String[] ffmpegCommand = {
					ffmpegBin, "-y",
					"-i", source.getClonePath(), 
					"-i", generateAssFile(source.getMetadataPackage(), source.getIntendedDestination()),
					"-scodec", "copy",
					"-vcodec", "copy",
					"-acodec", "copy",
					source.getAbsolutePath()
			};
			
			try {
				execProcess(ffmpegCommand, null);
			} catch (Exception e) {
				Log.e(InformaConstants.VIDEO_LOG, e.toString());
			}
		} catch (IOException e) {
			Log.e(InformaConstants.VIDEO_LOG, e.toString());
		} catch (JSONException e) {
			Log.e(InformaConstants.VIDEO_LOG, e.toString());
		}
	}
	
	public byte[] getBitStream(String video) {
		File h264 = new File(video.substring(0, video.lastIndexOf(ObscuraConstants.MimeTypes.MP4)) + "h264");
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		try {
			Runtime.getRuntime().exec("chmod 700 " +ffmpegBin);
		} catch (IOException e) {
			Log.e(InformaConstants.VIDEO_LOG, e.toString());
		}
		
		String[] ffmpegCommand = {
				ffmpegBin, "-y",
				"-i", video,
				"-vcodec", "copy",
				"-vbsf", "h264_mp4toannexb",
				"-an", h264.getAbsolutePath()
		};
		
		try {
			execProcess(ffmpegCommand, null);
			return fileToBytes(h264);
		} catch (Exception e) {
			Log.e(InformaConstants.VIDEO_LOG, e.toString());
			return new byte[]{};
		}
		
		
	}
	
	public String generateAssFile(JSONObject mdPack, String intendedDestination) throws IOException, JSONException {
		File ass = new File(InformaConstants.DUMP_FOLDER, intendedDestination + ".ass");
		if(!ass.exists())
			ass.createNewFile();
		
		// 1. load up temp ass
		BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("informa.ass")));
		String line, cloneLine = null;
		
		StringBuilder sb = new StringBuilder();
		while((line = br.readLine()) != null) {
			// 2. replace %vroot
			if(line.contains(Ass.VROOT))
				line = line.replace(Ass.VROOT, mdPack.getJSONObject(Keys.Informa.GENEALOGY).getString(Keys.Genealogy.LOCAL_MEDIA_PATH));
			cloneLine = new String(line);
			if(!line.contains(Ass.BLOCK_DATA))
				sb.append(line + "\n");
		}
		// 3. clone last line
		
		Log.d(InformaConstants.VIDEO_LOG, "clone line: " + cloneLine);
		
		long zero = mdPack.getJSONObject(Keys.Informa.GENEALOGY).getLong(Keys.Genealogy.DATE_CREATED);
		
		// 4. iterate over metadata and set vars
		// TODO: parse metadata as proper ass file
		JSONArray events = mdPack.getJSONObject(Keys.Informa.DATA).getJSONArray(Keys.Data.EVENTS);
		Map<Long, JSONObject> eventLog = new ConcurrentHashMap<Long, JSONObject>();
		for(int e=0; e<events.length(); e++) {
			JSONObject event = events.getJSONObject(e).getJSONObject(Events.EVENT_DATA);
			//{"eventData":{"captureEvent":{"device_bluetooth_name":"VIDEOCLASH (2)","device_bluetooth_address":"00:22:41:CB:4B:03"},"timestamp":1332786615932},"eventType":9}
			long timestamp = (Long) event.remove(Events.TIMESTAMP);
			event.remove(Events.TYPE);
						
			try {
				if(eventLog.containsKey(timestamp))
					eventLog.get(timestamp).accumulate(Events.CAPTURE_EVENT, event.get(Events.CAPTURE_EVENT));
				else {
					Log.d(InformaConstants.VIDEO_LOG, "event log has no entry for " + timestamp);
					eventLog.put(timestamp, event);
					// 1332798919020
				}
				
			} catch(NullPointerException npe) {
				Log.e(InformaConstants.VIDEO_LOG, "HEY WHAT: " + npe);
			}
						
		}
		mdPack.getJSONObject(Keys.Informa.DATA).remove(Keys.Data.EVENTS);
		
		
		JSONObject informa = new JSONObject();
		informa.put(Keys.Informa.GENEALOGY, mdPack.getJSONObject(Keys.Informa.GENEALOGY));
		informa.put(Keys.Informa.INTENT, mdPack.getJSONObject(Keys.Informa.INTENT));
		informa.put(Keys.Informa.DATA, mdPack.getJSONObject(Keys.Informa.DATA));
		eventLog.put(zero, informa);
		
		Log.d(InformaConstants.VIDEO_LOG, "log dump: " + eventLog.toString());		
		
		for(Entry<Long, JSONObject> entry : eventLog.entrySet()) {
			String cl = new String(cloneLine);
			
			long blockStart = Math.abs(entry.getKey() - zero);
			long blockEnd = blockStart + 200;
			
			cl = cl.replace(Ass.BLOCK_START, millisToHIS(blockStart));
			cl = cl.replace(Ass.BLOCK_END, millisToHIS(blockEnd));
			cl = cl.replace(Ass.BLOCK_DATA, entry.getValue().toString());
			sb.append(cl + "\n");
			
			Log.d(InformaConstants.VIDEO_LOG, "ts: " + entry.getKey() + "\nblockStart: " + blockStart + "\nblockEnd: " + blockEnd);
			Log.d(InformaConstants.VIDEO_LOG, "converted: " + millisToHIS(blockStart) + "/" + millisToHIS(blockEnd));
		}
		
		
		// 5. save file
		FileWriter fw = new FileWriter(ass.getAbsolutePath());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(sb.toString());
		bw.close();
		fw.close();
		Log.d(InformaConstants.VIDEO_LOG, "done saving " + ass.getAbsolutePath());
		return ass.getAbsolutePath();
	}
	
	private String millisToHIS(long millis) {
		SimpleDateFormat sdf = new SimpleDateFormat("mm:ss.SSS");
		String time = sdf.format(new Date(millis));
		return "00:" + time;
	}
	
	private byte[] fileToBytes(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		byte[] fileBytes = new byte[(int) file.length()];
		
		int offset = 0;
		int bytesRead = 0;
		while(offset < fileBytes.length && (bytesRead = fis.read(fileBytes, offset, fileBytes.length - offset)) >= 0)
			offset += bytesRead;
		fis.close();
		return fileBytes;
	}
		
	public void processVideo(File redactSettingsFile, 
			Vector<ObscureRegion> obscureRegions, File inputFile, File outputFile, String format, 
			int width, int height, int frameRate, int kbitRate, float sizeMult, ShellCallback sc) throws Exception {
		
		writeRedactData(redactSettingsFile, obscureRegions, sizeMult);
		    	
		
    	String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " +ffmpegBin);
    	
    	//ffmpeg -v 10 -y -i /sdcard/org.witness.sscvideoproto/videocapture1042744151.mp4 -vcodec libx264 -b 3000k -s 720x480 -r 30 -acodec copy -f mp4 -vf 'redact=/data/data/org.witness.sscvideoproto/redact_unsort.txt' /sdcard/org.witness.sscvideoproto/new.mp4
    	
    	String[] ffmpegCommand = {ffmpegBin, "-v", "10", "-y", "-i", inputFile.getPath(), 
				"-vcodec", "libx264", 
				"-b", kbitRate+"k", 
				"-s",  (int)(width*sizeMult) + "x" + (int)(height*sizeMult), 
				"-r", ""+frameRate,
				"-an",
				"-f", format,
				"-vf","redact=" + redactSettingsFile.getAbsolutePath(),
				outputFile.getPath()};
    	
    	//"-vf" , "redact=" + Environment.getExternalStorageDirectory().getPath() + "/" + PACKAGENAME + "/redact_unsort.txt",

    	
    	// Need to make sure this will create a legitimate mp4 file
    	//"-acodec", "ac3", "-ac", "1", "-ar", "16000", "-ab", "32k",
    	//"-acodec", "copy",

    	/*
    	String[] ffmpegCommand = {"/data/data/"+PACKAGENAME+"/ffmpeg", "-v", "10", "-y", "-i", recordingFile.getPath(), 
    					"-vcodec", "libx264", "-b", "3000k", "-vpre", "baseline", "-s", "720x480", "-r", "30",
    					//"-vf", "drawbox=10:20:200:60:red@0.5",
    					"-vf" , "\"movie="+ overlayImage.getPath() +" [logo];[in][logo] overlay=0:0 [out]\"",
    					"-acodec", "copy",
    					"-f", "mp4", savePath.getPath()+"/output.mp4"};
    	*/
    	
    	execProcess(ffmpegCommand, sc);
	    
	}
	
	public void doCleanup() {
		// TODO: remove ass files
	}
	
	private void writeRedactData(File redactSettingsFile, Vector<ObscureRegion> obscureRegions, float sizeMult) throws IOException {
		// Write out the finger data
					
		FileWriter redactSettingsFileWriter = new FileWriter(redactSettingsFile);
		PrintWriter redactSettingsPrintWriter = new PrintWriter(redactSettingsFileWriter);
		
		for (int i = 0; i < obscureRegions.size(); i++) {
			ObscureRegion or = (ObscureRegion)obscureRegions.get(i);
			String orData = or.getStringData(sizeMult);
			Log.d("SSC", orData);
			redactSettingsPrintWriter.println(orData);
		}
		redactSettingsPrintWriter.flush();
		redactSettingsPrintWriter.close();

				
	}
	
	class FileMover {

		InputStream inputStream;
		File destination;
		
		public FileMover(InputStream _inputStream, File _destination) {
			inputStream = _inputStream;
			destination = _destination;
		}
		
		public void moveIt() throws IOException {
		
			OutputStream destinationOut = new BufferedOutputStream(new FileOutputStream(destination));
				
			int numRead;
			byte[] buf = new byte[1024];
			while ((numRead = inputStream.read(buf) ) >= 0) {
				destinationOut.write(buf, 0, numRead);
			}
			    
			destinationOut.flush();
			destinationOut.close();
		}
	}

}


