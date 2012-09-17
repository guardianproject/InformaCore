package org.witness.informacam.app.editors.video;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import net.sqlcipher.database.SQLiteDatabase;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.ShellUtils.ShellCallback;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.transport.UploaderService;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.app.editors.video.ObscureRegion;
import org.witness.informacam.app.editors.video.RegionTrail;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.j3m.J3M;
import org.witness.informacam.j3m.J3M.J3MPackage;

import com.google.common.cache.LoadingCache;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

public class VideoConstructor {

	static String[] libraryAssets = {"ffmpeg"};
	static File fileBinDir;
	Context context;
	
	JSONObject metadataObject;
	ArrayList<Map<Long, String>> metadataForEncryption;
	final static String LOGTAG = App.LOG;
	DatabaseHelper dh;
	SQLiteDatabase db;
	SharedPreferences sp;
	
	File clone;
	
	public static VideoConstructor videoConstructor;

	public VideoConstructor(Context _context) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);

		if (!new File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
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
				if (sc != null) {
					Log.d(LOGTAG, "should be sending shellout " + line);
					sc.shellOut(line);
				} else
					Log.d(LOGTAG, "why is sc NULL?");
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
    	
    	clone = outputFile;
    	
    	
    	try {
    		Log.d(LOGTAG, "video constructor called");
			execProcess(ffmpegCommand, new ShellCallback ()
			{

				@Override
				public void shellOut(String shellLine) {
					Log.d(VideoEditor.LOGTAG, shellLine);
					
				}

				
				
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
	    
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
	
	public void buildInformaVideo(Context c, LoadingCache<Long, LogPack> annotationCache, long[] encryptList) {
		
		long saveTime = System.currentTimeMillis();
		InformaService.getInstance().informa.setSaveTime(saveTime);
		
		try {
			dh = DatabaseService.getInstance().getHelper();
			db = DatabaseService.getInstance().getDb();
			
			List<Entry<Long, LogPack>> annotations = InformaService.getInstance().getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED, annotationCache);
			if(InformaService.getInstance().informa.addToAnnotations(annotations)) {
				dh.setTable(db, Tables.Keys.KEYRING);
				
				for(long td : encryptList) {
					Cursor cursor = dh.getValue(db, null, TrustedDestination.Keys.KEYRING_ID, td);
					
					if(cursor != null && cursor.moveToFirst()) {
						String forName = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_DISPLAY_NAME));
						
						// add into intent
						InformaService.getInstance().informa.setTrustedDestination(cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS)));
						
						
						// bundle up informadata
						byte[] key = cursor.getBlob(cursor.getColumnIndex(PGP.Keys.PGP_PUBLIC_KEY));
						// XXX: encryption is broken, to fix later
						//String informaMetadata = EncryptionUtility.encrypt(InformaService.getInstance().informa.bundle().getBytes(), key);
						String informaMetadata = InformaService.getInstance().informa.bundle();
						
						
						// insert metadata
						File version = new File(Storage.FileIO.DUMP_FOLDER, System.currentTimeMillis() + "_" + forName.replace(" ", "-") + Media.Type.MKV);
						File mdFile = stringToFile(informaMetadata, Storage.FileIO.DUMP_FOLDER, Storage.FileIO.TMP_VIDEO_DATA_FILE_NAME);
						constructVideo(version, mdFile);
						
						// save metadata in database
						ContentValues cv = InformaService.getInstance().informa.initMetadata(MediaHasher.hash(version, "SHA-1") + "/" + version.getName(), cursor.getLong(cursor.getColumnIndex(Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID)));
						
						J3M j3m = new J3M(InformaService.getInstance().informa.getPgpKeyFingerprint(), cv, version);
						cv.put(Media.Keys.J3M_BASE, j3m.getBase());
						
						dh.setTable(db, Tables.Keys.MEDIA);
						db.insert(dh.getTable(), null, cv);
						
						UploaderService.getInstance().requestTicket(new J3MPackage(j3m, cursor.getString(cursor.getColumnIndex(TrustedDestination.Keys.URL)), td));
						
						cursor.close();
					}
				}
			}
			InformaService.getInstance().versionsCreated();
		} catch(IOException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (InterruptedException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		} catch (ExecutionException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public void constructVideo(File video, File metadata) throws IOException, JSONException {
		
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);
		
		String[] ffmpegCommand = new String[] {
			ffmpegBin, "-i", clone.getAbsolutePath(),
			"-attach", metadata.getAbsolutePath(),
			"-metadata:s:2", "mimetype=text/plain",
			"-vcodec", "copy",
			"-acodec", "copy",
			video.getAbsolutePath()
		};
		
		StringBuffer sb = new StringBuffer();
		for(String f: ffmpegCommand) {
			sb.append(f + " ");
		}
		Log.d(LOGTAG, "command to ffmpeg: " + sb.toString());
		
		try {
			execProcess(ffmpegCommand, new ShellCallback ()
			{

				@Override
				public void shellOut(String shellLine) {
					Log.d(VideoEditor.LOGTAG, shellLine);
					
				}

				
				
			});
		} catch (Exception e) {
			e.printStackTrace();
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