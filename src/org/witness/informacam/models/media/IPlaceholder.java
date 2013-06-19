package org.witness.informacam.models.media;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.utils.Constants.Models;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class IPlaceholder extends IMedia {
	public String index = null;
	
	private Context c;
	
	public IPlaceholder(JSONObject placeholder, Context c) {
		this.c = c;
		isNew = true;
		dcimEntry = new IDCIMEntry();
		
		bitmapList = String.valueOf(R.drawable.ic_new_photo_list);
		bitmapThumb = String.valueOf(R.drawable.ic_new_photo_thumb);
		
		try {
			dcimEntry.mediaType = placeholder.getString(Models.IDCIMEntry.MEDIA_TYPE);
			index = placeholder.getString(Models.IDCIMEntry.INDEX);
			
			if(dcimEntry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
				bitmapList = String.valueOf(R.drawable.ic_new_video_list);
				bitmapThumb = String.valueOf(R.drawable.ic_new_video_thumb);
			} else if(dcimEntry.mediaType.equals(Models.IMedia.MimeType.LOG)) {
				bitmapList = String.valueOf(R.drawable.ic_new_log_list);
				bitmapThumb = String.valueOf(R.drawable.ic_new_log_thumb);
			}
			
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
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
