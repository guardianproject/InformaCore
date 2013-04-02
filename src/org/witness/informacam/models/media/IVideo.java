package org.witness.informacam.models.media;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.models.IMedia;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

public class IVideo extends IMedia {
	public String bitmap, bitmapThumb, bitmapList, bitmapPreview = null;
	public int width, height;
	
	@Override
	public boolean embed(java.io.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
		VideoConstructor videoConstructor = new VideoConstructor(this, new info.guardianproject.iocipher.File(bitmap), j3m, pendingConnection);
		return videoConstructor.finish();
	}
	
	@Override
	public boolean embed(info.guardianproject.iocipher.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
		VideoConstructor videoConstructor = new VideoConstructor(this, new info.guardianproject.iocipher.File(bitmap), j3m, pendingConnection);
		return videoConstructor.finish();
	}
	
	@Override
	public void analyze() {
		super.analyze();
	
		InformaCam informaCam = InformaCam.getInstance();
		
		byte[] bytes = null;
		try {
			bytes = informaCam.ioService.getBytes(dcimEntry.getString("video_still"), Type.IOCIPHER);
			final Bitmap bitmap_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
			bytes = null;
			
			height = dcimEntry.exif.height;
			width = dcimEntry.exif.width;

			// 2. thumb
			info.guardianproject.iocipher.File bThumb = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.thumbnailName);
			informaCam.ioService.saveBlob(Base64.decode(informaCam.ioService.getBytes(dcimEntry.thumbnailFileName, Type.IOCIPHER), Base64.DEFAULT), bThumb);
			bitmapThumb = bThumb.getAbsolutePath();
			dcimEntry.thumbnailFile = null;
			dcimEntry.thumbnailFileName = null;

			// 3. list and preview
			String nameRoot = dcimEntry.name.substring(0, dcimEntry.name.lastIndexOf("."));
			final info.guardianproject.iocipher.File bList = new info.guardianproject.iocipher.File(rootFolder, (nameRoot + "_list.jpg"));
			final info.guardianproject.iocipher.File bPreview = new info.guardianproject.iocipher.File(rootFolder, (nameRoot + "_preview.jpg"));

			new Thread(new Runnable() {
				@Override
				public void run() {
					InformaCam informaCam = InformaCam.getInstance();

					byte[] listViewBytes = ImageUtility.downsampleImageForListOrPreview(bitmap_);
					informaCam.ioService.saveBlob(listViewBytes, bList);
					informaCam.ioService.saveBlob(listViewBytes, bPreview);

					bitmapPreview = bPreview.getAbsolutePath();
					bitmapList = bList.getAbsolutePath();

					listViewBytes = null;
					bitmap_.recycle();
				}
			}).start();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
	}
}
