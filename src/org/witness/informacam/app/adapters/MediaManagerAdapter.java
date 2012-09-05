package org.witness.informacam.app.adapters;

import java.util.ArrayList;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.adapters.AddressBookAdapter.OnAddressFocusedListener;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.MediaManagerUtility.MediaManagerDisplay;

import android.app.Activity;
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
			summary.append(media.get(position).getString(AddressBook.Keys.CONTACT_NAME) + "\n");
			summary.append(media.get(position).getString(AddressBook.Keys.CONTACT_EMAIL));
			
			TextView mediaSummary = (TextView) convertView.findViewById(R.id.media_summary);
			mediaSummary.setText(summary.toString());
			
			LinearLayout contactDetailsHolder = (LinearLayout) convertView.findViewById(R.id.contact_details_holder);
			contactDetailsHolder.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					((OnMediaFocusedListener) a).onMediaFocusedListener(position);
					return false;
				}
				
			});
		} catch (JSONException e) {}
		
		return convertView;
	}
}
