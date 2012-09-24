package org.witness.informacam.app.adapters;

import info.guardianproject.iocipher.File;

import java.util.List;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.adapters.MediaManagerAdapter.OnMediaFocusedListener;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Time;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Media.Manifest;
import org.witness.informacam.utils.MessageCenterUtility.MessageCenterDisplay;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MessageCenterAdapter extends BaseAdapter {
	List<MessageCenterDisplay> mcd;
	Activity a;
	
	public interface MessageCenterAdapterListener {
		public void onMessageClicked(Object obj);
	}
	
	public MessageCenterAdapter(Activity a, List<MessageCenterDisplay> mcd) {
		this.a = a;
		this.mcd = mcd;
	}
	@Override
	public int getCount() {
		return mcd.size();
	}

	@Override
	public Object getItem(int position) {
		return mcd.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		convertView = LayoutInflater.from(a.getBaseContext()).inflate(R.layout.messaagecenter_listadapter, null);
		
		StringBuilder summary = new StringBuilder();
		
		ImageButton messageThumb = (ImageButton) convertView.findViewById(R.id.message_thumb);
		if(mcd.get(position).thumbnail != null) {
			byte[] b = IOUtility.getBytesFromFile(new File(mcd.get(position).thumbnail));
			messageThumb.setImageBitmap(IOUtility.getBitmapFromBytes(b, false));
		}
		
		summary.append(mcd.get(position).alias + "\n" + a.getString(R.string.message_center_conversation_with) + " " + mcd.get(position).from + "\n");
		summary.append(a.getResources().getString(R.string.message_center_last_checked) + " ");
		summary.append(Time.millisecondsToDatestamp(mcd.get(position).lastCheckedForMessages));

		TextView messageSummary = (TextView) convertView.findViewById(R.id.message_summary);
		messageSummary.setText(summary.toString());
		
		LinearLayout messageDetailsHolder = (LinearLayout) convertView.findViewById(R.id.message_details_holder);
		((View) messageDetailsHolder).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				((MessageCenterAdapterListener) a).onMessageClicked(mcd.get(position));
			}
			
		});

		return convertView;
	}
	
}
