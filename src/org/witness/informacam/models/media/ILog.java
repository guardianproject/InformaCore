package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.List;


public class ILog extends IMedia {
	public long autoLogInterval = 10 * (60 * 1000);	// 10 minutes?
	public long startTime = 0L;
	public long endTime = 0L;
	public List<IMedia> attachedMedia = new ArrayList<IMedia>();
	
	public ILog() {
		super();
	}
}
