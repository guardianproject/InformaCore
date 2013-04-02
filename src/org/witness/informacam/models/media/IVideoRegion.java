package org.witness.informacam.models.media;

import java.util.List;

public class IVideoRegion extends IRegion {
	public List<IRect> trail = null;
	public long startTime = 0L;
	public long endTime = 0L;
	public long duration = 0L;
}
