package org.witness.informacam.utils.models;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class IDCIMDescriptor extends Model {
	public long startTime = 0L;
	public long endTime = 0L;
	public List<IDCIMEntry> dcimEntries = null;
	public List<IDCIMEntry> thumbnails = null;
	public int numEntries = 0;

	public void startSession() {
		startTime = System.currentTimeMillis();
		dcimEntries = new ArrayList<IDCIMEntry>();
		thumbnails = new ArrayList<IDCIMEntry>();
		Log.d(LOG, "starting dcim session");
	}

	public void stopSession() {
		endTime = System.currentTimeMillis();
		Log.d(LOG, "saved a dcim descriptor:\n" + asJson().toString());		
	}

	public void addEntry(Uri authority, Context c, boolean isThumbnail) {
		IDCIMEntry entry = new IDCIMEntry();
		try {
			entry.put(Models.IDCIMEntry.AUTHORITY, authority.toString());
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		String sortBy = "date_added DESC";

		if(isThumbnail) {
			sortBy = null;
			entry.mediaType = Models.IDCIMEntry.THUMBNAIL;
		}

		Cursor cursor = c.getContentResolver().query(authority, null, null, null, sortBy);
		if(cursor != null && cursor.moveToFirst()) {
			entry.fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DATA));

			if(!isThumbnail) {
				entry.timeCaptured = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns.DATE_ADDED));
				entry.mediaType = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.MIME_TYPE));
			}

			entry.id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID));
			cursor.close();
		}

		if(!isThumbnail) {
			dcimEntries.add(analyze(entry, c));
			commit(entry);
			numEntries++;
		} else {
			thumbnails.add(analyze(entry, c));
		}

	}

	private void commit(IDCIMEntry entry) {
		// delete/encrypt/replace all the data
		InformaCam informaCam = InformaCam.getInstance();

		info.guardianproject.iocipher.File reviewDump = new info.guardianproject.iocipher.File(Storage.REVIEW_DUMP);
		try {
			if(!reviewDump.exists()) {
				reviewDump.mkdir();
			}
		} catch(ExceptionInInitializerError e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		info.guardianproject.iocipher.File newFile = new info.guardianproject.iocipher.File(reviewDump, entry.name);
		info.guardianproject.iocipher.File newFileThumb = new info.guardianproject.iocipher.File(reviewDump, entry.thumbnailName);

		informaCam.ioService.saveBlob(
				informaCam.ioService.getBytes(entry.fileName, Type.FILE_SYSTEM), 
				newFile,
				true,
				entry.uri);
		informaCam.ioService.saveBlob(
				entry.thumbnailFile, 
				newFileThumb,
				true,
				null);

		entry.fileName = newFile.getAbsolutePath();
		entry.thumbnailFileName = newFileThumb.getAbsolutePath();
		entry.thumbnailFile = null;
	}

	private IDCIMEntry analyze(IDCIMEntry entry, Context c) {
		Log.d(LOG, "analyzing: " + entry.asJson().toString());

		java.io.File file = new java.io.File(entry.fileName);

		entry.name = file.getName();
		entry.size = file.length();
		entry.timeCaptured = file.lastModified();

		try {
			entry.hash = MediaHasher.hash(file, "SHA-1");

			if(entry.uri == null) {
				entry.uri = IOUtility.getUriFromFile(c, Uri.parse(entry.getString(Models.IDCIMEntry.AUTHORITY)), file).toString();
			}
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		if(entry.mediaType != Models.IDCIMEntry.THUMBNAIL) {
			Bitmap b = MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MICRO_KIND, null);
			if(b == null) {
				b = MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
			}

			entry.thumbnailFile = IOUtility.getBytesFromBitmap(b, true);
			b.recycle();

			String tPath = entry.name.substring(entry.name.lastIndexOf("."));
			entry.thumbnailName = entry.name.replace(tPath, "_thumb.jpg");

		}

		return entry;
	}
}
