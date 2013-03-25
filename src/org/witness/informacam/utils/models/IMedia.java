package org.witness.informacam.utils.models;

import android.graphics.Bitmap;

public class IMedia extends Model {
	public Bitmap bitmap, bitmapThumb, bitmapList, bitmapPreview;
	public String _id, _rev, alias;
	public int mediaType, orientation, width, height;
	
	public CharSequence detailsAsText;
}
