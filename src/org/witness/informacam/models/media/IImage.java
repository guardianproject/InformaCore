package org.witness.informacam.models.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.witness.informacam.Debug;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;

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
	public boolean analyze() throws IOException {
		super.analyze();
		
		if (Debug.WAIT_FOR_DEBUGGER)
			android.os.Debug.waitForDebugger();
		
		InformaCam informaCam = InformaCam.getInstance();

		InputStream isImage = informaCam.ioService.getStream(dcimEntry.fileName, Type.IOCIPHER);

		BitmapFactory.Options opts = new BitmapFactory.Options();		                            
		opts.inScaled = false; 		
		opts.inPurgeable=true;

		Bitmap bitmap_ = BitmapFactory.decodeStream(isImage, null, opts);
		height = bitmap_.getHeight();
		width = bitmap_.getWidth();
				
		info.guardianproject.iocipher.File b = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
		try {
			isImage.close();
			
			isImage = informaCam.ioService.getStream(dcimEntry.fileName, Type.IOCIPHER);
			
			informaCam.ioService.saveBlob(isImage, b);	
			
		} catch (IOException e) {

			Log.e(LOG,"error analyzing image",e);
			return false;
		}
		
		bitmap = b.getAbsolutePath();
		
		// hash
		if(genealogy == null) {
			genealogy = new IGenealogy();
		}
		
		//use the videocon hasher for images too... it works just fine
		//VideoConstructor vc = new VideoConstructor(informaCam);		
		//String hash = vc.hashMedia(Type.IOCIPHER, dcimEntry.fileName,"jpg");
		String hash = null;
		
		try
		{
			hash = MediaHasher.getJpegHash(informaCam.ioService.getStream(dcimEntry.fileName, Type.IOCIPHER));			
			Log.d(LOG,"import media hash:" + hash);
		}
		catch (Exception e)
		{
			Log.e(LOG,"error media hash",e);
				
		}
		
		genealogy.hashes = new ArrayList<String>();
		genealogy.hashes.add(hash);
		
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
