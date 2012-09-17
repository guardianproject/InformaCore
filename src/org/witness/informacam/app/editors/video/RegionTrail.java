package org.witness.informacam.app.editors.video;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.VideoRegion;

import android.graphics.Bitmap;
import android.util.Log;

public class RegionTrail {

	
	private HashMap<Integer,ObscureRegion> regionMap = new HashMap<Integer,ObscureRegion>();
	private Properties mProps;
	
	private int startTime = 0;
	private int endTime = 0;

	public static final String OBSCURE_MODE_REDACT = "black";
	public static final String OBSCURE_MODE_PIXELATE = "pixel";
	public static final String OBSCURE_MODE_IDENTIFY = "identify";
	
	private String obscureMode = OBSCURE_MODE_PIXELATE;
	
	private boolean doTweening = false;
	
	private VideoEditor videoEditor;
	
	public interface VideoRegionListener {
		public void onVideoRegionCreated(RegionTrail rt);
		public void onVideoRegionChanged(RegionTrail rt);
		public void onVideoRegionDeleted(RegionTrail rt);
	}
	
	public boolean isDoTweening() {
		return doTweening;
	}

	public void setDoTweening(boolean doTweening) {
		this.doTweening = doTweening;
	}
	
	public String getObscureMode() {
		return obscureMode;
	}

	public void setObscureMode(String obscureMode) {
		this.obscureMode = obscureMode;
	}
	
	public RegionTrail (int startTime, int endTime, VideoEditor videoEditor)
	{
		this(startTime, endTime, videoEditor, null, null, false, System.currentTimeMillis());
	}
	
	public RegionTrail(int startTime, int endTime, VideoEditor videoEditor, String obscureMode, List<ObscureRegion> videoTrail, boolean fromCache, long timestamp) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.videoEditor = videoEditor;
		
		if(obscureMode == null)
			setObscureMode(OBSCURE_MODE_PIXELATE);
		else
			setObscureMode(obscureMode);
		
		
		
		mProps = new Properties();
		mProps.put(VideoRegion.FILTER, this.obscureMode);
		mProps.put(VideoRegion.TIMESTAMP, timestamp);
		mProps.put(VideoRegion.START_TIME, startTime);
		mProps.put(VideoRegion.END_TIME, endTime);
		
		if(videoTrail != null)
			for(ObscureRegion or : videoTrail)
				addRegion(or);
		
		// CANNOT OVERWRITE IF FROM A CACHE!
		if(!fromCache)
			InformaService.getInstance().onVideoRegionCreated(this);
	}

	public int getStartTime() {
		return startTime;
	}

	public void setStartTime(int startTime) {
		this.startTime = startTime;
		mProps.put(VideoRegion.START_TIME, startTime);
	}

	public int getEndTime() {
		return endTime;
	}

	public void setEndTime(int endTime) {
		this.endTime = endTime;
		mProps.put(VideoRegion.END_TIME, endTime);
	}

	public void addRegion (ObscureRegion or)
	{
		regionMap.put(or.timeStamp,or);
		or.setRegionTrail(this);
		mProps.put(VideoRegion.FILTER, obscureMode);
	}
	
	public void removeRegion (ObscureRegion or)
	{
		regionMap.remove(or.timeStamp);
		mProps.put(VideoRegion.CHILD_REGIONS, regionMap.size());
		InformaService.getInstance().onVideoRegionDeleted(this);
	}
	
	private void updateChildMetadata() throws JSONException {
		JSONArray childMetadata = new JSONArray();
		Iterator<ObscureRegion> it = getRegionsIterator();
		while(it.hasNext()) {
			ObscureRegion or = it.next();
			JSONObject metadata = new JSONObject();
			metadata.put(VideoRegion.Child.COORDINATES, "[" + or.getBounds().top + "," + or.getBounds().left + "]");
			metadata.put(VideoRegion.Child.WIDTH, Integer.toString((int) Math.abs(or.getBounds().left - or.getBounds().right)));
			metadata.put(VideoRegion.Child.HEIGHT, Integer.toString((int) Math.abs(or.getBounds().top - or.getBounds().bottom)));
			metadata.put(VideoRegion.Child.TIMESTAMP, Integer.toString(or.timeStamp));
			childMetadata.put(metadata);
		}
		
		
		mProps.put(VideoRegion.TRAIL, childMetadata);
	}
	
	public void addIdentityTagger() {
		if(!mProps.containsKey(VideoRegion.Subject.PSEUDONYM)) {
			mProps.put(VideoRegion.Subject.PSEUDONYM, "");
			mProps.put(VideoRegion.Subject.INFORMED_CONSENT_GIVEN, "false");
			mProps.put(VideoRegion.Subject.PERSIST_FILTER, "false");
		}
	}
	
	public Iterator<ObscureRegion> getRegionsIterator ()
	{
		return regionMap.values().iterator();
	}
	
	public ObscureRegion getRegion (Integer key)
	{
		return regionMap.get(key);
	}
	
	public TreeSet<Integer> getRegionKeys ()
	{
		TreeSet<Integer> regionKeys = new TreeSet<Integer>(regionMap.keySet());

		return regionKeys;
	}
	
	public boolean isWithinTime (int time)
	{

		if (time < startTime || time > endTime)
			return false;
		else
			return true;
	}
	
	public ObscureRegion getCurrentRegion (int time, boolean doTween)
	{

		ObscureRegion regionResult = null;
		
		if (time < startTime || time > endTime)
			return null;
		else if (regionMap.size() > 0)
		{
		
			
			TreeSet<Integer> regionKeys = new TreeSet<Integer>(regionMap.keySet());
			
			Integer lastRegionKey = -1, regionKey = -1;
			
			Iterator<Integer> itKeys = regionKeys.iterator();
			
			while (itKeys.hasNext())
			{
				regionKey = itKeys.next();
				int comp = regionKey.compareTo(time);
				
				if (comp == 0 || comp == 1)
				{
					ObscureRegion regionThis = regionMap.get(regionKey);
					
					if (lastRegionKey != -1 && doTween)
					{
						ObscureRegion regionLast = regionMap.get(lastRegionKey);
					
						float sx, sy, ex, ey;
						
						int timeDiff = regionThis.timeStamp - regionLast.timeStamp;
						int timePassed = time - regionLast.timeStamp;
						
						float d = ((float)timePassed) / ((float)timeDiff);
						
						sx = regionLast.sx + ((regionThis.sx-regionLast.sx)*d);
						sy = regionLast.sy + ((regionThis.sy-regionLast.sy)*d);
						
						ex = regionLast.ex + ((regionThis.ex-regionLast.ex)*d);
						ey = regionLast.ey + ((regionThis.ey-regionLast.ey)*d);
						
						regionResult = new ObscureRegion(time, sx, sy, ex, ey);
						
					}
					else
						regionResult = regionThis;
					
					
					break; //it is a match!
				}
			

				lastRegionKey = regionKey;
				
			}
			
			if (regionResult == null)
				regionResult = regionMap.get(lastRegionKey);
			
			
		}
		
		return regionResult;
	}
	
	public JSONObject getRepresentation() throws JSONException {
		updateChildMetadata();
		JSONObject representation = new JSONObject();
		Enumeration<?> e = getProperties().propertyNames();
		
		while(e.hasMoreElements()) {
			String prop = (String) e.nextElement();
			representation.put(prop, getProperties().get(prop));
		}
		
		return representation;
	}
	
	public Properties getPrettyPrintedProperties() {
		Properties _mProps = (Properties) mProps.clone();
		_mProps.remove(VideoRegion.TRAIL);
		
		Log.d(App.LOG, "all props:\n" + _mProps.toString());
		
		return _mProps;
	}
	
	public Properties getProperties() {
		return mProps;
	}
	
	public void setProperties(Properties mProps) {
		this.mProps = mProps;
	}

	public void addSubject(String pseudonym, boolean informedConsentGiven, boolean persistFilter) {
		mProps.setProperty(Data.VideoRegion.Subject.PSEUDONYM, pseudonym);
		mProps.setProperty(Data.VideoRegion.Subject.INFORMED_CONSENT_GIVEN, String.valueOf(informedConsentGiven));
		mProps.setProperty(Data.VideoRegion.Subject.PERSIST_FILTER, String.valueOf(persistFilter));
	}
}
