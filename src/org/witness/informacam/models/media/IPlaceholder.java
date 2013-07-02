package org.witness.informacam.models.media;

import org.witness.informacam.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class IPlaceholder extends IMedia {
	public String index = null;
	
	private Context c;
	
	public IPlaceholder(String jobId, Context c) {
		this.c = c;
		isNew = true;
		
		bitmapList = String.valueOf(R.drawable.ic_new_photo_list);
		bitmapThumb = String.valueOf(R.drawable.ic_new_photo_thumb);
		
		index = jobId;
		
	}
	
	@Override
	public String renderDetailsAsText(int depth) {
		return c.getString(R.string.analyzing);
	}
	
	@Override
	public Bitmap getBitmap(String drawable) {
		return BitmapFactory.decodeResource(c.getResources(), Integer.parseInt(drawable));
	}

}
