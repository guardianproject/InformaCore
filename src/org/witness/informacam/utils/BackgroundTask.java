package org.witness.informacam.utils;

import java.io.Serializable;

@SuppressWarnings("serial")
public class BackgroundTask implements Serializable {
	private BackgroundProcessor backgroundProcessor = null;
	
	public BackgroundTask(BackgroundProcessor backgroundProcessor) {
		this.backgroundProcessor = backgroundProcessor;
	}
	
	public BackgroundTask getOnBatchCompleteTask() {
		return backgroundProcessor.onBatchComplete;
	}
	
	protected boolean onStart() {
		return true;
	}
	
	protected void onStop() {
		
	}
}