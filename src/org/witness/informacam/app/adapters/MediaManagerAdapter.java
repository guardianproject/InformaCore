package org.witness.informacam.app.adapters;

import info.guardianproject.iocipher.File;

import java.util.ArrayList;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.MediaManagerUtility.MediaManagerDisplay;
import org.witness.informacam.utils.Time;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MediaManagerAdapter extends BaseAdapter {
	ArrayList<MediaManagerDisplay> media;
	Activity a;
	
	public interface OnMediaFocusedListener {
		public void onItemLongClick(int which);
		public void onItemClick(Object obj);
	}
	
	public MediaManagerAdapter(Activity a, ArrayList<MediaManagerDisplay> media) {
		this.media = media;
		this.a = a;
	}
	
	@Override
	public int getCount() {
		return media.size();
	}

	@Override
	public Object getItem(int position) {
		return media.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		convertView = LayoutInflater.from(a.getApplicationContext()).inflate(R.layout.mediamanager_listadapter, null);
		
		try {
			StringBuilder summary = new StringBuilder();
			String mediaTitle = media.get(position).getString(Manifest.Keys.ALIAS);
			
			ImageButton mediaThumb = (ImageButton) convertView.findViewById(R.id.media_thumb);
			if(media.get(position).has(Media.Manifest.Keys.THUMBNAIL)) {
				
				byte[] b = IOUtility.getBytesFromFile(new File(media.get(position).getString(Media.Manifest.Keys.THUMBNAIL)));
				if(b != null)
					mediaThumb.setImageBitmap(IOUtility.getBitmapFromBytes(b, false));
			}
			
			summary.append(mediaTitle + "\n\n" + Media.Type.Names.get(media.get(position).getInt(Manifest.Keys.MEDIA_TYPE)) + "\n");
			summary.append(a.getResources().getString(R.string.media_manager_last_saved) + " ");
			summary.append(Time.millisecondsToDatestamp(media.get(position).getLong(Manifest.Keys.LAST_SAVED)));

			TextView mediaSummary = (TextView) convertView.findViewById(R.id.media_summary);
			mediaSummary.setText(summary.toString());
			
			LinearLayout mediaDetailsHolder = (LinearLayout) convertView.findViewById(R.id.media_details_holder);
			mediaDetailsHolder.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					((OnMediaFocusedListener) a).onItemLongClick(position);
					return false;
				}
				
			});
			
			mediaDetailsHolder.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					((OnMediaFocusedListener) a).onItemClick(media.get(position));
					
				}
				
			});
			
			
		} catch (JSONException e) {
			Log.e(App.LOG, e.toString());
			e.printStackTrace();
		}
		
		return convertView;
	}
}
