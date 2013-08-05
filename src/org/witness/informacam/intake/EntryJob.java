package org.witness.informacam.intake;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.json.JSONException;
import org.witness.informacam.informa.suckers.GeoSucker;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IExif;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.ILogPack;
import org.witness.informacam.models.media.IImage;
import org.witness.informacam.models.media.ILog;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IVideo;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.BackgroundProcessor;
import org.witness.informacam.utils.BackgroundTask;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Suckers.Geo;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.MediaStore;

public class EntryJob extends BackgroundTask {
	private static final long serialVersionUID = 3689090560752901928L;

	boolean isThumbnail;

	String parentId = null;
	String informaCache = null;
	long timeOffset = 0L;
	IDCIMEntry entry;

	protected final static String LOG = "************************** EntryJob **************************";

	public EntryJob(BackgroundProcessor backgroundProcessor, IDCIMEntry entry, String parentId, String informaCache, long timeOffset) {
		super(backgroundProcessor);

		this.entry = entry;
		this.parentId = parentId;
		this.informaCache = informaCache;
		this.timeOffset = timeOffset;
	}

	@Override
	protected boolean onStart() {

		try
		{
			analyze();

			if(entry != null) {
				if(!isThumbnail) {
					IMedia media = new IMedia();
					media.dcimEntry = entry;
					media._id = media.generateId(entry.originalHash);

					media.associatedCaches = new ArrayList<String>();
					media.associatedCaches.add(informaCache);

					media.genealogy = new IGenealogy();

					media.genealogy.dateCreated = media.dcimEntry.timeCaptured + timeOffset;

					if(this.parentId != null) {
						((ILog) informaCam.mediaManifest.getById(this.parentId)).attachedMedia.add(media._id);
						informaCam.mediaManifest.save();
					}

					boolean isFinishedProcessing = false;

					if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
						IImage image = new IImage(media);

						if(image.analyze()) {
							informaCam.mediaManifest.addMediaItem(image);
							isFinishedProcessing = true;
						}
					} else if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
						IVideo video = new IVideo(media);

						if(video.analyze()) {
							informaCam.mediaManifest.addMediaItem(video);
							isFinishedProcessing = true;
						}
					}

					if(isFinishedProcessing) {
						backgroundProcessor.numCompleted++;
						
						Bundle data = new Bundle();
						data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.DCIM.ADD);
						data.putString(Codes.Extras.CONSOLIDATE_MEDIA, entry.originalHash);
						data.putInt(Codes.Extras.NUM_PROCESSING, backgroundProcessor.numProcessing);
						data.putInt(Codes.Extras.NUM_COMPLETED, backgroundProcessor.numCompleted);

						Message message = new Message();
						message.setData(data);

						InformaCamEventListener mListener = informaCam.getEventListener();
						if (mListener != null) {
							mListener.onUpdate(message);
						} else {
							Logger.d(LOG, "I GUESS THE MLISTENER IS NULL");
						}
					}

				} else {
					((BatchCompleteJob) getOnBatchCompleteTask()).addThumbnail(entry);
				}
			}

		}
		catch (Exception e)
		{
			Logger.e(LOG, e);
		}


		return super.onStart();
	}

	private void parseExif() {
		entry.exif = new IExif();

		if (informaCam.informaService != null && informaCam.informaService._geo != null)
		{
			ILogPack logpack = ((GeoSucker) informaCam.informaService._geo).forceReturn();

			if (logpack != null)
			{
				try {
					entry.exif.location = Model.parseJSONAsFloatArray(logpack.getString(Geo.Keys.GPS_COORDS));
				} catch (JSONException e) {
					Logger.e(LOG, e);

				} catch(NullPointerException e) {
					Logger.e(LOG, e);
					entry.exif.location = new float[] {0f,0f};
				}
			}
			else
			{

				entry.exif.location = new float[] {0f,0f};
			}
		}

		if(entry.mediaType.equals(MimeType.IMAGE)) {
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
				Logger.e(LOG, e);
			}
		} else if(entry.mediaType.equals(MimeType.VIDEO)) {
			MediaMetadataRetriever mmr = new MediaMetadataRetriever();
			mmr.setDataSource(entry.fileName);

			entry.exif.duration = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
			entry.exif.width = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
			entry.exif.height = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
			try {
				entry.exif.orientation = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
			} catch(NumberFormatException e) {
				Logger.e(LOG, e);
				entry.exif.orientation = ExifInterface.ORIENTATION_NORMAL;
			}

			Logger.d(LOG, "VIDEO EXIF: " + entry.exif.asJson().toString());
			mmr.release();
		}
	}

	private void parseThumbnails() {
		Bitmap b = null;

		if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
			b = MediaStore.Images.Thumbnails.getThumbnail(this.informaCam.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MICRO_KIND, null);
			if(b == null) {
				b = MediaStore.Images.Thumbnails.getThumbnail(this.informaCam.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
			}

			MediaMetadataRetriever mmr = new MediaMetadataRetriever();
			mmr.setDataSource(entry.fileName);

			Bitmap b_ = mmr.getFrameAtTime();
			if(b_ == null) {
				Logger.d(LOG, "I COULD NOT GET A BITMAP AT ANY FRAME");
			} else {
				Logger.d(LOG, "got a video bitmap: (height " + b_.getHeight() + ")");
			}

			byte[] previewBytes = IOUtility.getBytesFromBitmap(b_, false);

			info.guardianproject.iocipher.File reviewDump = new info.guardianproject.iocipher.File(Storage.REVIEW_DUMP);
			try {
				if(!reviewDump.exists()) {
					reviewDump.mkdir();
				}
			} catch(ExceptionInInitializerError e) {
				Logger.e(LOG, e);
			}

			info.guardianproject.iocipher.File preview = new info.guardianproject.iocipher.File(reviewDump, "PREVIEW_" + entry.name);

			informaCam.ioService.saveBlob(previewBytes, preview);
			previewBytes = null;

			entry.previewFrame = preview.getAbsolutePath();

			if(b == null) {
				b = ImageUtility.createThumb(b_, new int[] {entry.exif.width, entry.exif.height});
			}

			b_.recycle();
			mmr.release();
		}

		if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
			InputStream is = informaCam.ioService.getStream(entry.fileName, Type.FILE_SYSTEM);

			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = 8;

			b = BitmapFactory.decodeStream(is, null, opts);

		}

		if(b != null) {
			entry.thumbnailFile = IOUtility.getBytesFromBitmap(b, true);
			b.recycle();

			String tPath = entry.name.substring(entry.name.lastIndexOf("."));
			entry.thumbnailName = entry.name.replace(tPath, "_thumb.jpg");
		}
	}

	private void analyze() throws IOException, NoSuchAlgorithmException, JSONException {
		java.io.File file = new java.io.File(entry.fileName);

		if(!entry.isAvailable()) {
			Logger.d(LOG, "ENTRY IS NULL!!!!!!!!  ABORTING?!");
			entry = null;
			return;
		}

		entry.name = file.getName();
		entry.size = file.length();
		entry.timeCaptured = file.lastModified();	// Questionable...?

		entry.originalHash = MediaHasher.hash(file, "SHA-1");

		if(entry.uri == null) {
			entry.uri = IOUtility.getUriFromFile(informaCam, Uri.parse(entry.authority), file).toString();
		}

		Logger.d(LOG, "analyzing: " + entry.asJson().toString());

		if(!entry.mediaType.equals(Models.IDCIMEntry.THUMBNAIL)) {
			parseExif();
			parseThumbnails();
			commit();
		}		
	}	

	private void commit() {
		// delete/encrypt/replace all the data
		info.guardianproject.iocipher.File reviewDump = new info.guardianproject.iocipher.File(Storage.REVIEW_DUMP);
		try {
			if(!reviewDump.exists()) {
				reviewDump.mkdir();
			}
		} catch(ExceptionInInitializerError e) {
			Logger.e(LOG, e);
		}

		info.guardianproject.iocipher.File newFile = new info.guardianproject.iocipher.File(reviewDump, entry.name);
		info.guardianproject.iocipher.File newFileThumb = new info.guardianproject.iocipher.File(reviewDump, entry.thumbnailName);


		try
		{
			//save the thumbnail first, don't delete, and there is no source URI
			informaCam.ioService.saveBlob(
					entry.thumbnailFile, 
					newFileThumb);

			entry.thumbnailFileName = newFileThumb.getAbsolutePath();
			entry.thumbnailFile = null;

			//now save the big one - delete the original file
			informaCam.ioService.saveBlob(
					new FileInputStream(entry.fileName), 
					newFile,
					entry.uri);

			entry.fileName = newFile.getAbsolutePath();
		}
		catch (IOException e)
		{
			Logger.e(LOG, e);
		}
	}
}
