package org.witness.informacam.app.mods;

public class Selections {
	public String _optionValue;
	boolean _selected;
	private Object _extras;
	
	public Selections(String optionValue, boolean selectDefault) {
		this(optionValue, selectDefault, null);
	}
	
	public Selections(String optionValue, boolean selectDefault, Object extras) {
		_optionValue = optionValue;
		_selected = selectDefault;
		_extras = extras;
	}
	
	public boolean getSelected() {
		return _selected;
	}
	
	public void setSelected(boolean selected) {
		_selected = selected;
	}
	
	public Object getExtras() {
		return _extras;
	}
}
