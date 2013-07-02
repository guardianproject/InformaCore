package org.witness.informacam.models.media;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

public class IImage extends IMedia {
	public String bitmap;

	public IImage() {
		super();
	}
	
	public IImage(IMedia media) {
		super();
		inflate(media.asJson());
	}

	@Override
	public boolean analyze() {
		super.analyze();

		InformaCam informaCam = InformaCam.getInstance();

		byte[] bytes = informaCam.ioService.getBytes(dcimEntry.fileName, Type.IOCIPHER);

		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = false;

		if (bytes == null)
			return false;
		
		final Bitmap bitmap_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
		height = bitmap_.getHeight();
		width = bitmap_.getWidth();

		info.guardianproject.iocipher.File b = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
		informaCam.ioService.saveBlob(bytes, b);
		
		// hash
		genealogy = new IGenealogy();
		genealogy.hashes = new ArrayList<String>();
		try {
			genealogy.hashes.add(MediaHasher.getBitmapHash(bitmap_));
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}


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
