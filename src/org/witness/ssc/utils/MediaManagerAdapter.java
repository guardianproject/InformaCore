package org.witness.ssc.utils;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.ssc.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaManagerAdapter extends BaseAdapter {
	ArrayList<Selections> selections;
	LayoutInflater li;
	
	public MediaManagerAdapter(Context c, ArrayList<Selections> selections) {
		this.selections = selections;
		li = LayoutInflater.from(c);
	}
	
	@Override
	public int getCount() {
		return selections.size();
	}

	@Override
	public Object getItem(int position) {
		return selections.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		convertView = li.inflate(R.layout.mediamanager_listview, null);
		JSONObject selectionExtras = selections.get(position).getExtras();
		TextView mediaText = (TextView) convertView.findViewById(R.id.mediaText);
		mediaText.setText(selections.get(position)._optionValue);
		
		ImageView mediaType = (ImageView) convertView.findViewById(R.id.mediaType);
		return convertView;
	}
}
