package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.embed.ImageConstructor;
import org.witness.informacam.models.connections.ISubmission;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

public class IImage extends IMedia {
	public String bitmap;
	public int width, height;
	
	@Override
	public boolean embed(java.io.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
		ImageConstructor imageConstructor = new ImageConstructor(this, new info.guardianproject.iocipher.File(bitmap), j3m, pendingConnection);
		return imageConstructor.finish();
	}
	
	@Override
	public boolean embed(info.guardianproject.iocipher.File destination, info.guardianproject.iocipher.File j3m, ISubmission pendingConnection) {
		ImageConstructor imageConstructor = new ImageConstructor(this, new info.guardianproject.iocipher.File(bitmap), j3m, pendingConnection);
		return imageConstructor.finish();
	}
	
	@Override
	public void analyze() {
		super.analyze();
		
		Log.d(LOG, "BUT NOW I DO IMAGE STUFF on " + dcimEntry.fileName);
		InformaCam informaCam = InformaCam.getInstance();
		
		byte[] bytes = informaCam.ioService.getBytes(dcimEntry.fileName, Type.IOCIPHER);
		
		final Bitmap bitmap_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		height = bitmap_.getHeight();
		width = bitmap_.getWidth();

		info.guardianproject.iocipher.File b = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
		informaCam.ioService.saveBlob(bytes, b);
		bitmap = b.getAbsolutePath();
		dcimEntry.fileName = null;
		
		bytes = null;
		
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
	}
}
