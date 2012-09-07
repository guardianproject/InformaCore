package org.witness.informacam.app.adapters;

import java.util.ArrayList;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.adapters.AddressBookAdapter.OnAddressFocusedListener;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.MediaManagerUtility.MediaManagerDisplay;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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
		public void onMediaFocusedListener(int which);
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
			ImageButton mediaThumb = (ImageButton) convertView.findViewById(R.id.media_thumb);
			//mediaThumb.setImageBitmap(IOUtility.getBitmapFromBytes(IOUtility.getBytesFromFile(media.get(index)), false));
			
			StringBuilder summary = new StringBuilder();
			String mediaTitle = media.get(position).getString(Manifest.Keys.LOCATION_OF_ORIGINAL);
			if(media.get(position).has(Manifest.Keys.ALIAS))
				mediaTitle = media.get(position).getString(Manifest.Keys.ALIAS);
			
			summary.append(mediaTitle + "\n\n");
			summary.append(a.getResources().getString(R.string.media_manager_last_saved) + " ");
			summary.append(media.get(position).getLong(Manifest.Keys.LAST_SAVED));
			
			Log.d(App.LOG, "shoudl say: " + summary.toString());
			
			TextView mediaSummary = (TextView) convertView.findViewById(R.id.media_summary);
			mediaSummary.setText(summary.toString());
			
			LinearLayout mediaDetailsHolder = (LinearLayout) convertView.findViewById(R.id.media_details_holder);
			mediaDetailsHolder.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					((OnMediaFocusedListener) a).onMediaFocusedListener(position);
					return false;
				}
				
			});
		} catch (JSONException e) {
			Log.e(App.LOG, "WHAT? " + e.toString());
		}
		
		return convertView;
	}
}
