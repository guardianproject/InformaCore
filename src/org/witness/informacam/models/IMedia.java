package org.witness.informacam.models;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

public class IMedia extends Model {
	public String rootFolder;
	public String bitmap, bitmapThumb, bitmapList, bitmapPreview;
	public String _id, _rev, alias;
	public int width, height;
	public long lastEdited = 0L;
	public boolean isNew = false;

	public IDCIMEntry dcimEntry;

	public IData data;
	public IIntent intent;
	public IGenealogy genealogy;
	public List<IAnnotation> annotations;
	public List<IMessage> messages;

	public CharSequence detailsAsText;

	public Bitmap getBitmap(String pathToFile) {
		return IOUtility.getBitmapFromFile(pathToFile, Type.IOCIPHER);
	}

	public boolean delete() {
		InformaCam informaCam = InformaCam.getInstance();

		if(informaCam.mediaManifest.media.remove(this)) {
			informaCam.ioService.delete(rootFolder, Type.IOCIPHER);
			informaCam.mediaManifest.save();
			return true;
		}

		return false;
	}

	public boolean rename() {
		Log.d(LOG, "RENAMING A MEDIA ENTRY: " + _id);

		return true;
	}

	public boolean export() {
		Log.d(LOG, "EXPORTING A MEDIA ENTRY: " + _id);
		return true;
	}

	public String renderDetailsAsText(int depth) {
		StringBuffer details = new StringBuffer();
		switch(depth) {
		case 1:
			if(this.alias != null) {
				details.append(this.alias);
			}
			details.append(this._id);
			Log.d(LOG, this.asJson().toString());

			break;
		}

		return details.toString();
	}

	public void analyze() {
		isNew = true;

		InformaCam informaCam = InformaCam.getInstance();
		try {
			info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(dcimEntry.originalHash);
			this.rootFolder = rootFolder.getAbsolutePath();

			if(!rootFolder.exists()) {
				rootFolder.mkdir();
			}
		} catch (ExceptionInInitializerError e) {}

		byte[] bytes = null;

		if(dcimEntry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
			bytes = informaCam.ioService.getBytes(dcimEntry.fileName, Type.IOCIPHER);
		} else {
			try {
				bytes = informaCam.ioService.getBytes(dcimEntry.getString("video_still"), Type.IOCIPHER);
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		final Bitmap bitmap_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		height = bitmap_.getHeight();
		width = bitmap_.getWidth();

		// burn copies in various sizes
		// 1. main (if image)
		if(dcimEntry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
			info.guardianproject.iocipher.File b = new info.guardianproject.iocipher.File(rootFolder, dcimEntry.name);
			informaCam.ioService.saveBlob(bytes, b);
			bitmap = b.getAbsolutePath();
			dcimEntry.fileName = null;
		}
		
		bytes = null;

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

	}

	public String generateId(String seed) {
		try {
			return MediaHasher.hash(KeyUtility.generatePassword(seed.getBytes()).getBytes(), "MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return seed;
	}	
}
