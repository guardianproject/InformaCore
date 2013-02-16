package org.witness.informacam.app.editors.video;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.Map.Entry;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.editors.filters.Filter;
import org.witness.informacam.app.editors.filters.InformaTagger;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.utils.FormUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Forms;
import org.witness.informacam.utils.Constants.Informa.Keys.Data;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.VideoRegion;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class RegionTrail implements OnActionItemClickListener {


	@SuppressLint("UseSparseArrays")
	private HashMap<Integer,ObscureRegion> regionMap = new HashMap<Integer,ObscureRegion>();
	private Properties mProps;

	private int startTime = 0;
	private int endTime = 0;


	private int SET_IN_POINT, SET_OUT_POINT, REMOVE_KEYFRAME, REMOVE_REGION, DO_TWEEN = 0;

	private String obscureMode = App.VideoEditor.OBSCURE_MODE_PIXELATE;

	private boolean doTweening = false;

	private VideoEditor videoEditor;

	private List<Filter> mFilters;
	private Map<Integer, JSONObject> plugins;
	public Filter currentFilter = null;

	private static String[] mFilterLabels = {
		"SetInPoint", 
		"SetOutPoint",
		"DoTween",
		"RemoveKeyFrame",
		"RemoveTrail"};
	
	private static Integer[] mFilterIcons = {
		R.drawable.ic_add,
		R.drawable.ic_add,
		android.R.drawable.ic_menu_rotate,
		R.drawable.ic_context_delete,
		R.drawable.ic_context_delete};

	QuickAction mPopupMenu;

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

	public void setObscureMode(Filter filter) {
		this.currentFilter = filter;
		this.obscureMode = this.currentFilter.process_tag;
	}

	public void setObscureMode(String filterName) {
		for(Filter filter : mFilters) {
			if(filter.process_tag.equals(filterName)) {
				setObscureMode(filter);
				break;
			}
		}

	}

	public RegionTrail (int startTime, int endTime, VideoEditor videoEditor)
	{
		this(startTime, endTime, videoEditor, null, null, false, InformaService.getInstance().getCurrentTime());
	}

	public RegionTrail(int startTime, int endTime, VideoEditor videoEditor, String obscureMode, List<ObscureRegion> videoTrail, boolean fromCache, long timestamp) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.videoEditor = videoEditor;

		mFilters = new ArrayList<Filter>();
		for(Filter filter : App.VideoEditor.INFORMA_CAM_PLUGINS) {
			if(filter.is_available)
				mFilters.add(filter);
		}

		plugins = FormUtility.getAnnotationPlugins(mFilters.size());
		Iterator<Entry<Integer, JSONObject>> pIt = plugins.entrySet().iterator();


		while(pIt.hasNext()) {
			Entry<Integer, JSONObject> plugin = pIt.next();
			try {
				mFilters.add(new Filter(plugin.getValue().getString(Forms.TITLE), R.drawable.ic_file_blue, InformaTagger.class, App.VideoEditor.OBSCURE_MODE_IDENTIFY));
			} catch (JSONException e) {
				Log.e(App.LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		// map remaining actions
		int o = mFilters.size(); 
		SET_IN_POINT = o++;
		SET_OUT_POINT = o++;
		DO_TWEEN = o++;
		REMOVE_KEYFRAME = o++;
		REMOVE_REGION = o++;
		
		// set default mode
		if(currentFilter == null)
			setObscureMode(mFilters.get(0));
		else
			setObscureMode(currentFilter);



		mProps = new Properties();
		mProps.put(VideoRegion.FILTER, this.obscureMode);
		mProps.put(VideoRegion.TIMESTAMP, timestamp);
		mProps.put(VideoRegion.START_TIME, startTime);
		mProps.put(VideoRegion.END_TIME, endTime);


		if(videoTrail != null)
			for(ObscureRegion or : videoTrail)
				addRegion(or);

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

	private void initPopup() {
		mPopupMenu = new QuickAction(videoEditor);

		ActionItem aItem;
		for (int i = 0; i < mFilters.size(); i++)
		{

			aItem = new ActionItem();
			aItem.setTitle(mFilters.get(i).label);

			aItem.setIcon(videoEditor.getResources().getDrawable(mFilters.get(i).resId));			

			mPopupMenu.addActionItem(aItem);

		}


		for (int i = 0; i < mFilterLabels.length; i++)
		{

			aItem = new ActionItem();
			aItem.setTitle(mFilterLabels[i]);

			aItem.setIcon(videoEditor.getResources().getDrawable(mFilterIcons[i]));			

			mPopupMenu.addActionItem(aItem);

		}

		mPopupMenu.setOnActionItemClickListener(this);
	}

	@SuppressLint("HandlerLeak")
	public void inflatePopup (boolean showDelayed, final int x, final int y) {
		if(mPopupMenu == null)
			initPopup();

		if (showDelayed) {
			// We need layout to pass again, let's wait a second or two
			new Handler() {
				@Override
				public void handleMessage(Message msg) {

					//float[] points = {mBounds.centerX(), mBounds.centerY()};		
					//mMatrix.mapPoints(points);


					//mPopupMenu.show(mImageEditor.getImageView(), (int)points[0], (int)points[1]);
					mPopupMenu.show(videoEditor.getImageView(), x, y);
				}
			}.sendMessageDelayed(new Message(), 300);
		} else {			

			//mPopupMenu.show(mImageEditor.getImageView(), (int)points[0], (int)points[1]);
			mPopupMenu.show(videoEditor.getImageView(), x, y);
		}

	}

	public void inflatePopup(boolean showDelayed) {




	}

	public void addIdentityTagger() {
		if(!mProps.containsKey(VideoRegion.Subject.FORM_NAMESPACE)) {
			mProps.put(VideoRegion.Subject.FORM_NAMESPACE, "");
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
		return _mProps;
	}

	public Properties getProperties() {
		return mProps;
	}

	public void setProperties(Properties mProps) {
		this.mProps = mProps;
	}

	public void addSubject(String form_namespace, String form_data) {
		mProps.put(VideoRegion.Subject.FORM_NAMESPACE, form_namespace);
		mProps.put(VideoRegion.Subject.FORM_DATA, form_data);
	}

	@Override
	// TODO:
	public void onItemClick(QuickAction source, int pos, int actionId) {


		if (pos >= mFilters.size()) //meaing after the last one
		{
			if(pos == SET_IN_POINT) {
				setStartTime(videoEditor.mediaPlayer.getCurrentPosition());
				videoEditor.updateProgressBar(this);
			}

			if(pos == SET_OUT_POINT) {
				setEndTime(videoEditor.mediaPlayer.getCurrentPosition());
				videoEditor.updateProgressBar(this);
				videoEditor.activeRegion = null;
				videoEditor.activeRegionTrail = null;
			}

			if(pos == DO_TWEEN) {
				setDoTweening(!isDoTweening());
			}

			if(pos == REMOVE_KEYFRAME) {
				videoEditor.obscureTrails.remove(this);
				videoEditor.activeRegionTrail = null;
				videoEditor.activeRegion = null;
			}

			if(pos == REMOVE_REGION) {
				if (videoEditor.activeRegion != null)
				{
					removeRegion(videoEditor.activeRegion);
					videoEditor.activeRegion = null;
				}
			}
		} else {
			setObscureMode(mFilters.get(pos));
			mProps.put(Data.VideoRegion.FILTER, this.obscureMode);
			
			if(mFilters.get(pos).process_tag.equals(App.VideoEditor.OBSCURE_MODE_IDENTIFY)) {				
				Log.d(App.LOG, this.getPrettyPrintedProperties().toString());
				
				try {
					mProps.put(Data.VideoRegion.Subject.FORM_NAMESPACE, plugins.get(pos).getString(Forms.TITLE));
					mProps.put(Data.VideoRegion.Subject.FORM_DEF_PATH, plugins.get(pos).getString(Forms.DEF));
					
					// is it new? or is it a different filter than before?
					if(
							!mProps.containsKey(Data.VideoRegion.Subject.FORM_NAMESPACE) || 
							!plugins.get(pos).getString(Forms.TITLE).equals(mProps.get(Data.VideoRegion.Subject.FORM_NAMESPACE))
					) {
						
						if(mProps.containsKey(Data.VideoRegion.Subject.FORM_DATA))
							mProps.remove(Data.VideoRegion.Subject.FORM_DATA);
					}
					
				} catch (JSONException e) {
					Log.e(App.LOG, e.toString());
					e.printStackTrace();
				}
				
				
				videoEditor.launchTagger(this);
			} else {
				if(mProps.containsKey(Data.VideoRegion.Subject.FORM_NAMESPACE))
					mProps.remove(Data.VideoRegion.Subject.FORM_NAMESPACE);
				if(mProps.containsKey(Data.VideoRegion.Subject.FORM_DEF_PATH))
					mProps.remove(Data.VideoRegion.Subject.FORM_DEF_PATH);
				if(mProps.containsKey(Data.VideoRegion.Subject.FORM_DATA))
					mProps.remove(Data.VideoRegion.Subject.FORM_DATA);
			}
		}

		videoEditor.updateRegionDisplay(videoEditor.mediaPlayer.getCurrentPosition());

	}
}
