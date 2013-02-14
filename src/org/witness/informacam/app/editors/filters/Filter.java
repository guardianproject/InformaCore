package org.witness.informacam.app.editors.filters;

public class Filter {
	public String label;
	public int resId;
	public Class<?> regionProcessorClass;
	public String process_tag;
	public boolean is_available;
	
	public Filter(String label, int resId, Class<?> regionProcessorClass, String process_tag) {
		this(label, resId, regionProcessorClass, process_tag, true);
	}
	
	public Filter(String label, int resId, Class<?> regionProcessorClass, String process_tag, boolean is_available) {
		this.label = label;
		this.resId = resId;
		this.regionProcessorClass = regionProcessorClass;
		this.process_tag = process_tag;
		
		setAvailability(is_available);
	}
	
	public void setAvailability(boolean is_available) {
		this.is_available = is_available;
	}
}
