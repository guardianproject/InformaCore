package org.witness.informa.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.EncryptActivity.MetadataPack;
import org.witness.informa.Informa.Video;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Genealogy;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Keys.Ass;
import org.witness.informa.utils.InformaConstants.Keys.Data;
import org.witness.informa.utils.InformaConstants.Keys.Exif;
import org.witness.informa.utils.InformaConstants.Keys.Informa;
import org.witness.informa.utils.InformaConstants.Keys.Media;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.VideoRegion;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.MediaHasher;
import org.witness.ssc.video.BinaryInstaller;
import org.witness.ssc.video.ObscureRegion;
import org.witness.ssc.video.RegionTrail;
import org.witness.ssc.video.ShellUtils.ShellCallback;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class VideoConstructor {

	static String[] libraryAssets = {"ffmpeg"};
	static File fileBinDir;
	Context context;
	
	JSONObject metadataObject;
	ArrayList<Map<Long, String>> metadataForEncryption;
	final static String LOGTAG = InformaConstants.TAG;
	DatabaseHelper dh;
	SQLiteDatabase db;
	SharedPreferences sp;
	
	File clone;

	public VideoConstructor(Context _context) throws FileNotFoundException, IOException {
		this(_context, null);
	}
	
	public VideoConstructor(Context _context, String metadataObjectString) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);

		if (!new File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}
		
		if(metadataObjectString != null) {
			try {
				metadataObject = (JSONObject) new JSONTokener(metadataObjectString).nextValue();
				clone = new File(metadataObject.getJSONObject(Keys.Informa.GENEALOGY).getString(Keys.Genealogy.LOCAL_MEDIA_PATH));
				Log.d(LOGTAG, "CLONE: " + clone.getAbsolutePath());
			} catch (JSONException e) {
				Log.d(LOGTAG, "metadata object is not valid: " + e.toString());
			}
		}
	}
	
	
	
	private static void execProcess(String[] cmds, ShellCallback sc) throws Exception {		
        
		
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
	
	public void processVideo(File redactSettingsFile, 
			ArrayList<RegionTrail> obscureRegionTrails, File inputFile, File outputFile, String format, int mDuration,
			int iWidth, int iHeight, int oWidth, int oHeight, int frameRate, int kbitRate, String vcodec, String acodec, ShellCallback sc) throws Exception {
		
		float widthMod = ((float)oWidth)/((float)iWidth);
		float heightMod = ((float)oHeight)/((float)iHeight);
		
		writeRedactData(redactSettingsFile, obscureRegionTrails, widthMod, heightMod, mDuration);
		    	
		if (vcodec == null)
			vcodec = "copy";//"libx264"
		
		if (acodec == null)
			acodec = "copy";
		
    	String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " +ffmpegBin);
    	
    	String[] ffmpegCommand = {ffmpegBin, "-v", "10", "-y", "-i", inputFile.getPath(), 
				"-vcodec", vcodec, 
				"-b", kbitRate+"k", 
				"-s",  oWidth + "x" + oHeight, 
				"-r", ""+frameRate,
				"-acodec", "copy",
				"-f", format,
				"-vf","redact=" + redactSettingsFile.getAbsolutePath(),
				outputFile.getPath()};
    	
    	
    	//ffmpeg -v 10 -y -i /sdcard/org.witness.sscvideoproto/videocapture1042744151.mp4 -vcodec libx264 -b 3000k -s 720x480 -r 30 -acodec copy -f mp4 -vf 'redact=/data/data/org.witness.sscvideoproto/redact_unsort.txt' /sdcard/org.witness.sscvideoproto/new.mp4
    	
    	//"-vf" , "redact=" + Environment.getExternalStorageDirectory().getPath() + "/" + PACKAGENAME + "/redact_unsort.txt",

    	
    	// Need to make sure this will create a legitimate mp4 file
    	//"-acodec", "ac3", "-ac", "1", "-ar", "16000", "-ab", "32k",
    	

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
	
	private void writeRedactData(File redactSettingsFile, ArrayList<RegionTrail> regionTrails, float widthMod, float heightMod, int mDuration) throws IOException {
		// Write out the finger data
					
		FileWriter redactSettingsFileWriter = new FileWriter(redactSettingsFile);
		PrintWriter redactSettingsPrintWriter = new PrintWriter(redactSettingsFileWriter);
		ObscureRegion or = null, lastOr = null;
		String orData = "";
		
		for (RegionTrail trail : regionTrails)
		{
			
			if (trail.isDoTweening())
			{
				int timeInc = 100;
				
				for (int i = 0; i < mDuration; i = i+timeInc)
				{
					or = trail.getCurrentRegion(i, trail.isDoTweening());
					if (or != null)
					{
						orData = or.getStringData(widthMod, heightMod,i,timeInc, trail.getObscureMode());
						redactSettingsPrintWriter.println(orData);
					}
				}
				
			}
			else
			{
				
				for (Integer orKey : trail.getRegionKeys())
				{
					or = trail.getRegion(orKey);
					
					if (lastOr != null)
					{
						
						orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
					}
					
					redactSettingsPrintWriter.println(orData);
					
					lastOr = or;
				}
				
				if (or != null)
				{
					orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
					redactSettingsPrintWriter.println(orData);
				}
			}
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
	
	public void createVersionForTrustedDestination(Video v) throws JSONException, NoSuchAlgorithmException, IOException {
		JSONObject mediaHash = new JSONObject();
		String unredactedHash = MediaHasher.hash(clone, "SHA-1");
		
		metadataForEncryption = new ArrayList<Map<Long, String>>();
		
		sp = PreferenceManager.getDefaultSharedPreferences(context);
		
		dh = new DatabaseHelper(context);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		mediaHash.put(Keys.Image.UNREDACTED_IMAGE_HASH, unredactedHash);
		//mediaHash.put(Keys.Image.REDACTED_IMAGE_HASH, redactedHash);
		metadataObject.getJSONObject(Keys.Informa.DATA).put(Keys.Data.MEDIA_HASH, mediaHash);
		
		// replace the metadata's intended destination
		metadataObject.getJSONObject(Keys.Informa.INTENT).put(Keys.Intent.INTENDED_DESTINATION, v.getIntendedDestination());
		
		// insert into database for later
		ContentValues cv = new ContentValues();
		// package zipped image region bytes
		cv.put(Keys.Image.METADATA, metadataObject.toString());
		cv.put(Keys.Image.REDACTED_IMAGE_HASH, "null");
		cv.put(Keys.Image.LOCATION_OF_ORIGINAL, ((JSONObject) metadataObject.getJSONObject(Informa.GENEALOGY)).getString(Keys.Image.LOCAL_MEDIA_PATH));
		cv.put(Keys.Image.LOCATION_OF_OBSCURED_VERSION, v.getAbsolutePath());
		cv.put(Keys.Image.TRUSTED_DESTINATION, v.getIntendedDestination());
		cv.put(Keys.Image.CONTAINMENT_ARRAY, "null");
		cv.put(Keys.Image.UNREDACTED_IMAGE_HASH, unredactedHash);
		cv.put(Keys.Media.MEDIA_TYPE, MediaTypes.VIDEO);
		
		dh.setTable(db, Tables.IMAGES);
		Map<Long, String> mo = new HashMap<Long, String>();
		mo.put(db.insert(dh.getTable(), null, cv), v.getAbsolutePath());
		metadataForEncryption.add(mo);
	}
	
	public void doCleanup() {
		db.close();
		dh.close();
	}
	
	public static long constructVideo(Context c, MetadataPack metadataPack, ShellCallback sc) throws IOException, JSONException {
		fileBinDir = c.getDir("bin",0);

		if (!new File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(c, fileBinDir);
			bi.installFromRaw();
		}
		
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);
		
		JSONObject metadataObject = (JSONObject) new JSONTokener(metadataPack.metadata).nextValue();
		long rootTime = ((JSONObject) metadataObject.get(Informa.GENEALOGY)).getLong(Genealogy.DATE_CREATED);
		int duration = ((JSONObject) ((JSONObject) metadataObject.get(Informa.DATA)).get(Keys.Data.EXIF)).getInt(Keys.Exif.DURATION);
		
		JSONArray timedData = (JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.VIDEO_REGIONS);
		JSONArray locations = (JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.LOCATIONS);
		JSONArray captureTimestamps = (JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.CAPTURE_TIMESTAMPS);
		JSONArray corroboration = (JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.CORROBORATION);
		
		BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open("informa.ass")));
		String line;
		//Dialogue: 0,0:00:27.04,0:00:28.21,Main,,0000,0000,0000,,{\be1}Um...
		String cloneLine = null;
		StringBuilder sb = new StringBuilder();
		while((line = br.readLine()) != null) {
			if(line.contains(Ass.VROOT))
				line = line.replace(Ass.VROOT, metadataPack.filepath);
			if(line.contains("Dialog")) {
				cloneLine = line;
				line = line.replace(Ass.BLOCK_START, InformaConstants.millisecondsToTimestamp(1L));
				line = line.replace(Ass.BLOCK_END, InformaConstants.millisecondsToTimestamp((long) duration));
				line = line.replace(Ass.BLOCK_DATA, metadataObject.toString());
			}
			sb.append(line).append('\n');
		}
		br.close();
		
		// replace the first line in the ass file with the remainder of the metadataObject
		for(int t=0; t<timedData.length(); t++) {
			// group the rest by timestamp and format for ass file
			JSONObject td = timedData.getJSONObject(t);
			line = cloneLine;
			line = line.replace(Ass.BLOCK_START, InformaConstants.millisecondsToTimestamp((long) td.getInt(Keys.VideoRegion.START_TIME)));
			line = line.replace(Ass.BLOCK_END, InformaConstants.millisecondsToTimestamp((long) td.getInt(VideoRegion.END_TIME)));
			line = line.replace(Ass.BLOCK_DATA, td.toString());
			
			sb.append(line).append('\n');
		}
		
		for(int t=0; t<corroboration.length(); t++) {
			JSONObject cd = corroboration.getJSONObject(t);
			long timeSeen = rootTime - cd.getLong(Keys.Device.TIME_SEEN);
			line = cloneLine;
			
			line = line.replace(Ass.BLOCK_START, InformaConstants.millisecondsToTimestamp(timeSeen, duration));
			line = line.replace(Ass.BLOCK_END, InformaConstants.millisecondsToTimestamp(timeSeen + 1000, duration));
			line = line.replace(Ass.BLOCK_DATA, cd.toString());
			
			sb.append(line).append('\n');
		}
		
		for(int t=0; t<captureTimestamps.length(); t++) {
			JSONObject ctd = captureTimestamps.getJSONObject(t);
			long timeSeen = rootTime - ctd.getLong(Keys.CaptureEvent.TIMESTAMP);
			for(int l=0; l<locations.length(); l++) {
				JSONObject ld = locations.getJSONObject(l);
				if(ld.getInt(Keys.Location.TYPE) == ctd.getInt(Keys.CaptureTimestamp.TYPE))
					line = line.replace(Ass.BLOCK_DATA, ld.toString());
			}
			line = cloneLine;
			line = line.replace(Ass.BLOCK_START, InformaConstants.millisecondsToTimestamp(timeSeen, duration));
			line = line.replace(Ass.BLOCK_END, InformaConstants.millisecondsToTimestamp(timeSeen + 1000, duration));
			
			sb.append(line).append('\n');
		}
		
		File mdfile = stringToFile(sb.toString(), InformaConstants.DUMP_FOLDER, Ass.TEMP);
		
		String[] ffmpegCommand = new String[] {
			ffmpegBin, "-i", metadataPack.clonepath,
			"-i", mdfile.getAbsolutePath(),
			"-scodec", "copy",
			"-vcodec", "copy",
			"-acodec", "copy",
			metadataPack.filepath
		};
		
		try {
			execProcess(ffmpegCommand, sc);
			return rootTime;
		} catch (Exception e) {
			Log.e(LOGTAG, e.toString());
			return 0L;
		}
	}
	
	private static File stringToFile(String data, String dir, String filename) {
		File file = new File(dir, filename);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			out.write(data);
			out.close();
			return file;
		} catch(IOException e) {
			return null;
		}
		
	}

}