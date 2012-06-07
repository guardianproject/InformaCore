package org.witness.ssc.video;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys.VideoRegion;
import org.witness.ssc.image.ImageRegion;

import android.graphics.Bitmap;
import android.util.Log;

public class RegionTrail {

	
	private HashMap<Integer,ObscureRegion> regionMap = new HashMap<Integer,ObscureRegion>();
	private Properties mProps;
	
	private int startTime = 0;
	private int endTime = 0;
	
	public RegionTrail (int startTime, int endTime)
	{
		this.startTime = startTime;
		this.endTime = endTime;
		
		mProps = new Properties();
		mProps.put(VideoRegion.FILTER, this.getClass().getName());
		mProps.put(VideoRegion.TIMESTAMP, System.currentTimeMillis());
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
		mProps.put(VideoRegion.FILTER, or.currentMode);
	}
	
	public void removeRegion (ObscureRegion or)
	{
		regionMap.remove(or.timeStamp);
		mProps.put(VideoRegion.CHILD_REGIONS, regionMap.size());
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
			childMetadata.put(metadata);
			Log.d(InformaConstants.TAG, or.toString());
		}
		
		
		mProps.put(VideoRegion.TRAIL, childMetadata.toString());
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
	
	public ObscureRegion getCurrentRegion (int time)
	{
		
		if (time < startTime || time > endTime)
			return null;
		else if (regionMap.size() > 0)
		{
		
			
			TreeSet<Integer> regionKeys = new TreeSet<Integer>(regionMap.keySet());
			
			Integer lastRegionKey = -1;
			
			for (Integer regionKey:regionKeys)
			{
				int comp = regionKey.compareTo(time);
				lastRegionKey = regionKey;
				
				if (comp == 0 || comp == 1)
					break; //it is a match!
				
			}
			
			return regionMap.get(lastRegionKey);
		}
		else
			return null;
	}
	
	public JSONObject getRepresentation() throws JSONException {
		JSONObject representation = new JSONObject();
		Enumeration<?> e = getProperties().propertyNames();
		
		while(e.hasMoreElements()) {
			String prop = (String) e.nextElement();
			representation.put(prop, getProperties().get(prop));
		}
		
		return representation;
	}
	
	public Properties getProperties() {
		try {
			updateChildMetadata();
		} catch(JSONException e) {
			Log.d(InformaConstants.TAG, "region trail error: " + e.toString());
		}
		return mProps;
	}
	
	public void setProperties(Properties mProps) {
		this.mProps = mProps;
	}
}
