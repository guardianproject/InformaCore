package org.witness.informacam.models.j3m;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.media.IImage;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IVideo;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Suckers.Geo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class IDCIMDescriptor extends Model {
	public long startTime = 0L;
	public long endTime = 0L;
	public List<IMedia> dcimEntries = null;
	public List<IDCIMEntry> thumbnails = null;
	public int numEntries = 0;

	public IDCIMDescriptor() {
		super();
	}

	public void startSession() {
		startTime = System.currentTimeMillis();
		dcimEntries = new ArrayList<IMedia>();
		thumbnails = new ArrayList<IDCIMEntry>();
		Log.d(LOG, "starting dcim session");
	}

	public void stopSession() {
		endTime = System.currentTimeMillis();
		Log.d(LOG, "saved a dcim descriptor:\n" + asJson().toString());		
	}

	public void addEntry(final Uri authority, final Context c, final boolean isThumbnail) {
		new Thread(new Runnable() {
			@Override
			public void run() {
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
						
						if(entry.mediaType.equals(MimeType.VIDEO_3GPP)) {
							entry.mediaType = MimeType.VIDEO;
						}
						
						numEntries++;
					}

					entry.id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID));
					cursor.close();
				}


				entry = analyze(entry, c);
				if(entry != null) {
					if(!isThumbnail) {
						IMedia media = new IMedia();
						media.dcimEntry = entry;
						media._id = media.generateId(entry.originalHash);

						if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
							IImage image = new IImage(media);
							image.analyze();
							dcimEntries.add(image);
						} else if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
							IVideo video = new IVideo(media);
							video.analyze();
							dcimEntries.add(video);
						}

						
						Log.d(LOG, "DCIM DESCRIPTOR UPDATED BY SELF:\n" + asJson().toString());
					} else {
						thumbnails.add(entry);
					}
				}
			}
		}).start();


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

		newFile = null;
		newFileThumb = null;
	}
	
	public boolean isFinishedAnalyzing() {
		do {
			Log.d(LOG, "actually, not yet. dcimEntries are at size " + dcimEntries.size());
		} while(dcimEntries.size() < numEntries);

		return true;
	}

	@SuppressLint("InlinedApi")
	private IDCIMEntry analyze(IDCIMEntry entry, Context c) {
		try {
			Log.d(LOG, "analyzing: " + entry.asJson().toString());

			java.io.File file = new java.io.File(entry.fileName);

			entry.name = file.getName();
			entry.size = file.length();
			entry.timeCaptured = file.lastModified();

			if(entry.timeCaptured < startTime) {
				return null;
			}

			if(!entry.isAvailable()) {
				return null;
			}

			entry.originalHash = MediaHasher.hash(file, "SHA-1");

			if(entry.uri == null) {
				entry.uri = IOUtility.getUriFromFile(c, Uri.parse(entry.getString(Models.IDCIMEntry.AUTHORITY)), file).toString();
			}
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (NullPointerException e) {
			Log.e(LOG, e.toString());
			return null;
		}

		if(!entry.mediaType.equals(Models.IDCIMEntry.THUMBNAIL)) {
			boolean getThumbnailFromMediaMetadata = false;
			boolean bruteForceThumbnailFromMedia = false;

			Bitmap b = null;			
			b = MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MICRO_KIND, null);
			if(b == null) {
				b = MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
			}

			if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
				getThumbnailFromMediaMetadata = true;
				Log.d(LOG, "definitely getting THUMBNAIL from METADATA RESOURCE");
			}
			
			if(b == null && entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
				bruteForceThumbnailFromMedia = true;
			}

			entry.exif = new IExif();

			try {
				entry.exif.location = Model.parseJSONAsFloatArray(((GeoSucker) InformaCam.getInstance().informaService._geo).forceReturn().getString(Geo.Keys.GPS_COORDS));
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch(NullPointerException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				entry.exif.location = new float[] {0f,0f};
			}

			Log.d(LOG, "MEDIA TYPE: " + entry.mediaType);
			if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
				try {
					ExifInterface ei = new ExifInterface(entry.fileName);

					entry.exif.aperture = ei.getAttribute(ExifInterface.TAG_APERTURE);
					entry.exif.timestamp = ei.getAttribute(ExifInterface.TAG_DATETIME);
					entry.exif.exposure = ei.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
					entry.exif.flash = ei.getAttributeInt(ExifInterface.TAG_FLASH, -1);
					entry.exif.focalLength = ei.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH, -1);
					entry.exif.iso = ei.getAttribute(ExifInterface.TAG_ISO);
					entry.exif.make = ei.getAttribute(ExifInterface.TAG_MAKE);
					entry.exif.model = ei.getAttribute(ExifInterface.TAG_MODEL);
					entry.exif.orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
					entry.exif.whiteBalance = ei.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1);
					entry.exif.width = ei.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
					entry.exif.height = ei.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);
				} catch (IOException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}

				if(bruteForceThumbnailFromMedia) {
					byte[] bytes = InformaCam.getInstance().ioService.getBytes(entry.fileName, Type.FILE_SYSTEM);
					Bitmap b_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
					b = ImageUtility.createThumb(b_, new int[] {b_.getWidth(), b_.getHeight()});
					entry.exif.width = b_.getWidth();
					entry.exif.height = b_.getHeight();
					b_.recycle();


				}
			} else {
				Log.d(LOG, "VIDEO entry: " + entry.asJson().toString());
				
				MediaMetadataRetriever mmr = new MediaMetadataRetriever();
				mmr.setDataSource(entry.fileName);

				entry.exif.duration = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
				entry.exif.width = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
				entry.exif.height = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
				try {
					entry.exif.orientation = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
				} catch(NumberFormatException e) {
					// returned null
					entry.exif.orientation = ExifInterface.ORIENTATION_NORMAL;
				}
				Log.d(LOG, "VIDEO EXIF: " + entry.exif.asJson().toString());
				
				if(getThumbnailFromMediaMetadata) {
					Bitmap b_ = mmr.getFrameAtTime();
					if(b_ == null) {
						Log.d(LOG, "I COULD NOT GET A BITMAP AT ANY FRAME");
					} else {
						Log.d(LOG, "got a video bitmap: (height " + b_.getHeight() + ")");
					}
					
					byte[] previewBytes = IOUtility.getBytesFromBitmap(b_, false);

					info.guardianproject.iocipher.File reviewDump = new info.guardianproject.iocipher.File(Storage.REVIEW_DUMP);
					try {
						if(!reviewDump.exists()) {
							reviewDump.mkdir();
						}
					} catch(ExceptionInInitializerError e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}

					info.guardianproject.iocipher.File preview = new info.guardianproject.iocipher.File(reviewDump, "PREVIEW_" + entry.name);
					InformaCam.getInstance().ioService.saveBlob(previewBytes, preview);
					previewBytes = null;

					entry.previewFrame = preview.getAbsolutePath();

					if(b == null) {
						b = ImageUtility.createThumb(b_, new int[] {entry.exif.width, entry.exif.height});
					}
					
					b_.recycle();
				}
			}

			if(b != null) {
				entry.thumbnailFile = IOUtility.getBytesFromBitmap(b, true);
				b.recycle();

				String tPath = entry.name.substring(entry.name.lastIndexOf("."));
				entry.thumbnailName = entry.name.replace(tPath, "_thumb.jpg");
			}

			commit(entry);

		}

		return entry;
	}
}
