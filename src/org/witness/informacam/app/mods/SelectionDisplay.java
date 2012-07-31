package org.witness.informacam.app.mods;

import java.util.ArrayList;

import org.witness.informacam.R;
import org.witness.informacam.app.adapters.SelectionsAdapter;
import org.witness.informacam.utils.Constants;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

public class SelectionDisplay extends LinearLayout {
	ArrayList<Selections> selections;
	public ListView selectionsList;
	public Button ok;
	
	public SelectionDisplay(Context context) {
		super(context);
	}
	
	public SelectionDisplay(Context context, ArrayList<Selections> selections) {
		super(context);
		this.selections = selections;
		
		View inner = LayoutInflater.from(context).inflate(R.layout.selection_display, null);
		selectionsList = (ListView) inner.findViewById(R.id.selection_display);
		selectionsList.setAdapter(new SelectionsAdapter(context, selections, Constants.Mods.Selections.SELECT_ONE));
		
		ok = (Button) inner.findViewById(R.id.selection_commit);
		
		this.addView(inner);
		
		
	}
	
	

}
