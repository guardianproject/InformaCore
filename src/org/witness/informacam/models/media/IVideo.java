package org.witness.informacam.models.media;

import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.embed.VideoConstructor;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

public class IVideo extends IMedia {
	public String video = null;
	
	public IVideo() {
		super();
	}
	
	public IVideo(IMedia media) {
		super();
		inflate(media.asJson());
	}
	
	@Override
	public Bitmap getBitmap(String pathToFile) {
		return IOUtility.getBitmapFromFile(bitmapThumb, Type.IOCIPHER);
	}

	@Override
	public boolean analyze() {
		super.analyze();

		InformaCam informaCam = InformaCam.getInstance();

		height = dcimEntry.exif.height;
		width = dcimEntry.exif.width;
		
		// 1. hash
		VideoConstructor vc = new VideoConstructor(informaCam);
		if(genealogy == null) {
			genealogy = new IGenealogy();
		}
		
		genealogy.hashes = vc.hashVideo(dcimEntry.fileName);
		
		// 2. copy over video
		info.guardianproject.iocipher.File videoFile = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
		informaCam.ioService.saveBlob(informaCam.ioService.getBytes(dcimEntry.fileName, Type.IOCIPHER), videoFile);
		informaCam.ioService.delete(dcimEntry.fileName, Type.IOCIPHER);
		dcimEntry.fileName = videoFile.getAbsolutePath();
		video = videoFile.getAbsolutePath();

		// 3. thumb
		info.guardianproject.iocipher.File bThumb = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.thumbnailName);
		informaCam.ioService.saveBlob(Base64.decode(informaCam.ioService.getBytes(dcimEntry.thumbnailFileName, Type.IOCIPHER), Base64.DEFAULT), bThumb);
		bitmapThumb = bThumb.getAbsolutePath();
		dcimEntry.thumbnailFile = null;
		dcimEntry.thumbnailFileName = null;

		// 4. list and preview
		String nameRoot = dcimEntry.name.substring(0, dcimEntry.name.lastIndexOf("."));
		final info.guardianproject.iocipher.File bList = new info.guardianproject.iocipher.File(rootFolder, (nameRoot + "_list.jpg"));
		final info.guardianproject.iocipher.File bPreview = new info.guardianproject.iocipher.File(rootFolder, (nameRoot + "_preview.jpg"));

		byte[] bytes = informaCam.ioService.getBytes(dcimEntry.previewFrame, Type.IOCIPHER);
		Bitmap bitmap_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		bytes = null;

		byte[] listViewBytes = ImageUtility.downsampleImageForListOrPreview(bitmap_);
		informaCam.ioService.saveBlob(listViewBytes, bList);
		informaCam.ioService.saveBlob(listViewBytes, bPreview);

		bitmapPreview = bPreview.getAbsolutePath();
		bitmapList = bList.getAbsolutePath();

		listViewBytes = null;
		bitmap_.recycle();
	
		return true;
	}
}
