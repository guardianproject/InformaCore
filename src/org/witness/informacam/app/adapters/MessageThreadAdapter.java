package org.witness.informacam.app.adapters;

import info.guardianproject.iocipher.R;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.witness.informacam.utils.Time;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class MessageThreadAdapter extends BaseAdapter {
	List<Map<Long, String>> messages;
	Context c;
	
	public MessageThreadAdapter(Context c, List<Map<Long, String>> messages) {
		this.messages = messages;
		this.c = c;
	}
	@Override
	public int getCount() {
		return messages.size();
	}

	@Override
	public Object getItem(int position) {
		return messages.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		convertView = LayoutInflater.from(c).inflate(R.layout.messagethread_listadapter, null);
		Entry<Long, String> m = messages.get(position).entrySet().iterator().next();
		
		TextView date = (TextView) convertView.findViewById(R.id.message_thread_date);
		date.setText(Time.millisecondsToDatestamp(m.getKey()));
		
		TextView content = (TextView) convertView.findViewById(R.id.message_thread_content);
		content.setText(m.getValue());
		
		return convertView;
	}

}
