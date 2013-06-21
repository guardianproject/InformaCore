package org.witness.informacam.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import android.view.View;

@SuppressWarnings("serial")
public class LanguageMap implements Serializable {
	private HashMap<Integer, Language> languages;
	
	public LanguageMap() {
		languages = new HashMap<Integer, Language>();
	}
	
	public void add(String code, String label) {
		languages.put(languages.size(), new Language(code, label));
	}
	
	public String getCode(int which) {
		return languages.get(which).code;
	}
	
	public ArrayList<String> getLabels() {
		ArrayList<String> labels = new ArrayList<String>();
		Iterator<Entry<Integer, Language>> it = languages.entrySet().iterator();
		while(it.hasNext()) {
			labels.add(it.next().getValue().label);
		}
		
		return labels;
	}
	
	public static void forceUpdate(View holder) {
		
	}
	
	public class Language implements Serializable {
		String code = null;
		String label = null;
		
		public Language(String code, String label) {
			this.code = code;
			this.label = label;
		}
	}
}
