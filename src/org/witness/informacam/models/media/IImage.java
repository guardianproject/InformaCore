package org.witness.informacam.models.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.witness.informacam.Debug;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models.IUser;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class IImage extends IMedia {
	public IImage() {
		super();
	}
	
	public IImage(IMedia media) throws InstantiationException, IllegalAccessException {
		super();
		inflate(media.asJson());
	}

	@Override
	public boolean analyze() throws IOException {
		super.analyze();
		
		InformaCam informaCam = InformaCam.getInstance();


		BitmapFactory.Options opts = new BitmapFactory.Options();	
		opts.inJustDecodeBounds = true;

		InputStream isImage = informaCam.ioService.getStream(dcimEntry.fileAsset.path, dcimEntry.fileAsset.source);
		BitmapFactory.decodeStream(isImage, null, opts);
		height = opts.outHeight;
		width = opts.outWidth;	
		isImage.close();
		
		// hash
		if(genealogy == null) {
			genealogy = new IGenealogy();
		}
		
		String hash = null;
		
		try
		{
			isImage = informaCam.ioService.getStream(dcimEntry.fileAsset.path, dcimEntry.fileAsset.source);
			hash = MediaHasher.getJpegHash(isImage);
			isImage.close();
			
		}
		catch (Exception e)
		{
			Log.e(LOG,"error media hash",e);
		}
		
		genealogy.hashes = new ArrayList<String>();
		genealogy.hashes.add(hash);
				
		// 3. list and preview
		/**
		int sampleSize = ImageUtility.calculateInSampleSize(opts, 320, 240);	
		isImage = informaCam.ioService.getStream(dcimEntry.fileAsset.path, dcimEntry.fileAsset.source);
		byte[] listViewBytes = ImageUtility.downsampleImage(isImage, sampleSize);
		isImage.close();
		
		if((Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
			info.guardianproject.iocipher.File preview = new info.guardianproject.iocipher.File(dcimEntry.originalHash, "PREVIEW_" + dcimEntry.name);
			info.guardianproject.iocipher.File list_view = new info.guardianproject.iocipher.File(dcimEntry.originalHash, "LIST_VIEW_" + dcimEntry.name);
			
			informaCam.ioService.saveBlob(listViewBytes, preview);
			informaCam.ioService.saveBlob(listViewBytes, list_view);
			
			dcimEntry.preview = new IAsset(preview.getAbsolutePath());
			dcimEntry.list_view = new IAsset(list_view.getAbsolutePath());
		} else {
			java.io.File preview = new java.io.File(IOUtility.buildPublicPath(new String [] {dcimEntry.originalHash}), "PREVIEW_" + dcimEntry.name);
			java.io.File list_view = new java.io.File(IOUtility.buildPublicPath(new String [] {dcimEntry.originalHash}), "LIST_VIEW_" + dcimEntry.name);
			
			try {
				informaCam.ioService.saveBlob(listViewBytes, preview, true);
				informaCam.ioService.saveBlob(listViewBytes, list_view, true);
			} catch (IOException e) {
				Logger.e(LOG, e);
			}
			
			dcimEntry.preview = new IAsset(preview.getAbsolutePath());
			dcimEntry.list_view = new IAsset(list_view.getAbsolutePath());
		}
		
		listViewBytes = null;
		*/
		
		return true;
	}
}