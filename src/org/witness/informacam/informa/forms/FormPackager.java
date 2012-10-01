package org.witness.informacam.informa.forms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.Forms;

import android.content.Context;
import android.util.Log;

public class FormPackager {
	public List<InformaCamForm> availableForms;
	
	public FormPackager(Context c) {
		availableForms = new ArrayList<InformaCamForm>();
		
		try {
			for(String dir : c.getAssets().list("installedKeys")) {
				// open the dir
				for(String form : c.getAssets().list("installedKeys/" + dir)) {
					// get any .informaCamForm
					InformaCamForm icf = new InformaCamForm();
					if(form.endsWith(Forms.MIME_TYPE)) {
						icf.path = "installedKeys/" + dir + "/" + form;
						Log.d(App.LOG, form);
						StringTokenizer st = new StringTokenizer(form, ".");
						icf.name = st.nextToken();
						availableForms.add(icf);
					}
						
				}
			}
		} catch (IOException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		}
	}
	
	public String getFormAtPosition(int pos) {
		return availableForms.get(pos).path;
	}
	
	public String[] getNames() {
		List<String> names = new ArrayList<String>();
		for(InformaCamForm icf : availableForms)
			names.add(icf.name);
		
		return names.toArray(new String[names.size()]);
	}
	
	public String[] getPaths() {
		List<String> paths = new ArrayList<String>();
		for(InformaCamForm icf : availableForms)
			paths.add(icf.path);
		
		return paths.toArray(new String[paths.size()]);
	}
}
