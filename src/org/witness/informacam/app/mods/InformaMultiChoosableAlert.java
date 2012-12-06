package org.witness.informacam.app.mods;

import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.R;
import org.witness.informacam.app.adapters.SelectionsAdapter;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.utils.Constants.Mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

public class InformaMultiChoosableAlert extends AlertDialog implements View.OnClickListener {
	Context context;
	LayoutInflater li;
	ArrayList<Selections> selections;
	
	View inner;
	ListView choiceList;
	TextView choiceTitle;
	InformaButton submit;
	
	InformaMultiChoosableAlert imca;
	
	public interface OnMultipleSelectedListener {
		public void onMultipleSelected(ArrayList<Selections> positiveSelections);
	}
	
	public InformaMultiChoosableAlert(Activity context, ArrayList<Selections> selections) {
		super(context);
		this.context = context;
		this.selections = selections;
				
		li = LayoutInflater.from(context);
		inner = li.inflate(R.layout.choosablealert_listview, null);
		this.setView(inner);
		
		submit = (InformaButton) inner.findViewById(R.id.choice_submit);
		submit.setOnClickListener(this);
		
		choiceList = (ListView) inner.findViewById(R.id.choice_list);
		choiceList.setAdapter(new SelectionsAdapter(context, selections, Mods.Selections.SELECT_MULTI));
		
		choiceTitle = (TextView) inner.findViewById(R.id.choice_title);
		imca = this;
		
	}
	
	
	public InformaMultiChoosableAlert(Context context) {
		super(context);
	}
	
	private ArrayList<Selections> getPositiveSelections() {
		ArrayList<Selections> positiveSelections = null;
		for(Selections s : selections) {
			if(s._selected) {
				if(positiveSelections == null)
					positiveSelections = new ArrayList<Selections>();
				
				positiveSelections.add(s);
			}
				
		}
		
		return positiveSelections;
	}
	
	@Override
	public void setTitle(CharSequence title) {
		
		choiceTitle.setText(title);
		choiceTitle.setVisibility(View.VISIBLE);
		
	}
	
	@Override
	public void onBackPressed() {
		imca.dismiss();
		((OnChoosableChosenListener) context).onCancel();
	}
	
	@Override
	public void onClick(View v) {
		imca.dismiss();
		((OnMultipleSelectedListener) context).onMultipleSelected(getPositiveSelections());
	}

}
