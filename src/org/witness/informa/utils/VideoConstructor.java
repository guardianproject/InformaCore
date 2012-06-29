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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.Informa.Video;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Genealogy;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.InformaConstants.Keys.Ass;
import org.witness.informa.utils.InformaConstants.Keys.Informa;
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
	
	public static VideoConstructor videoConstructor;

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
		
		videoConstructor = this;
	}
	
	public static VideoConstructor getVideoConstructor() {
		return videoConstructor;
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
						if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY)) {
							orData = or.getStringData(widthMod, heightMod,i,timeInc, trail.getObscureMode());
							redactSettingsPrintWriter.println(orData);
						}
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
						if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY))
							orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
					}
					
					
					redactSettingsPrintWriter.println(orData);
					
					lastOr = or;
				}
				
				if (or != null)
				{
					if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY)) {
						orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
						redactSettingsPrintWriter.println(orData);
					}
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
	
	public void createVersionForTrustedDestination(Video v) throws NoSuchAlgorithmException, IOException, JSONException {
		Log.d(LOGTAG, "creating version for TD NOW");
		JSONObject mediaHash = new JSONObject();
		List<JSONObject> eventLog = new CopyOnWriteArrayList<JSONObject>();
		List<JSONArray> events = new ArrayList<JSONArray>();
		JSONArray e = new JSONArray();
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
		
		// format the metadata for video
		long rootTime = ((JSONObject) metadataObject.get(Informa.GENEALOGY)).getLong(Genealogy.DATE_CREATED);
		
		events.add((JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.VIDEO_REGIONS));
		events.add((JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.LOCATIONS));
		events.add((JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.CAPTURE_TIMESTAMPS));
		events.add((JSONArray) ((JSONObject) metadataObject.get(Informa.DATA)).remove(Keys.Data.CORROBORATION));
		
		//Log.d(LOGTAG, "events: " + events.size());
		
		for(JSONArray j : events) {
			for(int i=0; i<j.length(); i++)
				e.put(j.get(i));
		}
		
		
		for(int t=0; t<e.length(); t++) {
			JSONObject td = e.getJSONObject(t);
			String time = InformaConstants.millisecondsToTimestamp(0);
			if(td.has(Keys.VideoRegion.START_TIME))
				time = InformaConstants.millisecondsToTimestamp((long) td.getInt(Keys.VideoRegion.START_TIME));
			
			boolean newEvent = true;
			
			Log.d(LOGTAG, "this event: " + td.toString());
			
			Iterator<JSONObject> eIt = eventLog.iterator();
			while(eIt.hasNext()) {
				
				JSONObject evt = eIt.next();
				if(evt.has(time)) {
					evt.accumulate(time, td);
					newEvent = false;
					continue;
				}
			}
			
			if(newEvent) {
				JSONObject newEvt = new JSONObject();
				newEvt.put(time, td);
				eventLog.add(newEvt);
			}
		}
		
		metadataObject.put(InformaConstants.Keys.Video.VIDEO_TRACK, eventLog.toString());
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
		// TODO: remove vidtmp.mp4, output***.txt, ass.ass, handle original video
		
	}
	
	public long constructVideo(MetadataPack metadataPack, ShellCallback sc) throws IOException, JSONException {
		// TODO: IF THIS IS ENCRYPTED AS A BLOB, THEN WHAT?
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);
		
		JSONObject metadataObject = (JSONObject) new JSONTokener(metadataPack.metadata).nextValue();
		long rootTime = ((JSONObject) metadataObject.get(Informa.GENEALOGY)).getLong(Genealogy.DATE_CREATED);
		
		File mdFile = new File(InformaConstants.DUMP_FOLDER, InformaConstants.TMP_VIDEO_DATA_FILE_NAME);
		String mdData = (metadataPack.encryptedMetadata == null ? metadataPack.metadata : metadataPack.encryptedMetadata);
		
		PrintWriter pw = new PrintWriter(mdFile);
		pw.print(mdData);
		pw.close();
		
		String[] ffmpegCommand = new String[] {
			ffmpegBin, "-i", metadataPack.clonepath,
			"-attach", mdFile.getAbsolutePath(),
			"-metadata:s:2", "mimetype=text/plain",
			"-vcodec", "copy",
			"-acodec", "copy",
			metadataPack.filepath
		};
		
		StringBuffer sb = new StringBuffer();
		for(String f: ffmpegCommand) {
			sb.append(f + " ");
		}
		Log.d(LOGTAG, "command to ffmpeg: " + sb.toString());
		
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