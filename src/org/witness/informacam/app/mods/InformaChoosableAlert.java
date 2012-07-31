package org.witness.informacam.app.mods;

import org.witness.informacam.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class InformaChoosableAlert extends AlertDialog {
	Context context;
	LayoutInflater li;
	String[] choices;
	View inner;
	ListView choiceList;
	TextView choiceTitle;
	
	Object obj;
	InformaChoosableAlert ica;
	
	public interface OnChoosableChosenListener {
		public void onChoice(int which, Object obj);
	}
	
	public InformaChoosableAlert(Activity context, String[] choices, Object obj) {
		super(context);
		this.context = context;
		this.choices = choices;
		this.obj = obj;
		
		li = LayoutInflater.from(context);
		inner = li.inflate(R.layout.choosablealert_listview, null);
		this.setView(inner);
		
		choiceList = (ListView) inner.findViewById(R.id.choice_list);
		choiceList.setAdapter(new ChoiceAdapter());
		
		choiceTitle = (TextView) inner.findViewById(R.id.choice_title);
		ica = this;
	}
	
	
	public InformaChoosableAlert(Context context) {
		super(context);
	}
	
	@Override
	public void setTitle(CharSequence title) {
		
		choiceTitle.setText(title);
		choiceTitle.setVisibility(View.VISIBLE);
		
	}
	
	public class ChoiceAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return choices.length;
		}

		@Override
		public Object getItem(int position) {
			return choices[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			convertView = li.inflate(R.layout.choosablealert_listadapter, null);
			TextView choice = (TextView) convertView.findViewById(R.id.choice_text);
			choice.setText(choices[position]);
			
			convertView.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					((OnChoosableChosenListener) context).onChoice(position, obj);
					ica.dismiss();
				}
				
			});
			
 			return convertView;
		}
		
	}

}
