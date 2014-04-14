package org.witness.informacam.intake;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONException;
import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IGenealogy;
import org.witness.informacam.models.j3m.IIntakeData;
import org.witness.informacam.models.media.IAsset;
import org.witness.informacam.models.media.IImage;
import org.witness.informacam.models.media.ILog;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IVideo;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.BackgroundProcessor;
import org.witness.informacam.utils.BackgroundTask;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Models.IUser;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.TimeUtility;

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

	protected String parentId = null;
	protected String[] informaCache = null;
	protected long timeOffset = 0L;
	protected IDCIMEntry entry;

	protected final static String LOG = "************************** EntryJob **************************";

	public EntryJob(BackgroundProcessor backgroundProcessor, IDCIMEntry entry, String parentId, String[] informaCache, long timeOffset) {
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
					media.dcimEntry.timezone = TimeUtility.getTimezone();
					media._id = media.generateId(entry.originalHash);

					media.associatedCaches = new ArrayList<String>();
					media.associatedCaches.addAll(Arrays.asList(informaCache));
					media.genealogy = new IGenealogy();

					media.genealogy.dateCreated = media.dcimEntry.timeCaptured;
					
					if(this.parentId != null) {
						((ILog) informaCam.mediaManifest.getById(this.parentId)).attachedMedia.add(media._id);
						informaCam.mediaManifest.save();
					}
					
					boolean isFinishedProcessing = false;

					if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
						IImage image = new IImage(media);

						if(image.analyze()) {
							image.intakeData = new IIntakeData(image.dcimEntry.timeCaptured, image.dcimEntry.timezone, timeOffset, ArrayUtils.toString(image.genealogy.hashes), image.dcimEntry.cameraComponent);
							Logger.d(LOG, image.intakeData.asJson().toString());
							
							informaCam.mediaManifest.addMediaItem(image);
							isFinishedProcessing = true;
						}
						
					
					} else if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
						IVideo video = new IVideo(media);

						if(video.analyze()) {
							video.intakeData = new IIntakeData(video.dcimEntry.timeCaptured, video.dcimEntry.timezone, timeOffset, ArrayUtils.toString(video.genealogy.hashes), video.dcimEntry.cameraComponent);
							Logger.d(LOG, video.intakeData.asJson().toString());

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
		if(entry.mediaType.equals(MimeType.IMAGE)) {
			try {
				ExifInterface ei = new ExifInterface(entry.fileAsset.path);

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
			mmr.setDataSource(entry.fileAsset.path);

			entry.exif.duration = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
			
			/*
			 * these keys are min API 14
			 */
			try {
				entry.exif.width = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
				entry.exif.height = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
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
			mmr.setDataSource(entry.fileAsset.path);

			Bitmap b_ = mmr.getFrameAtTime();
			if(b_ == null) {
				Logger.d(LOG, "I COULD NOT GET A BITMAP AT ANY FRAME");
			} else {
				Logger.d(LOG, "got a video bitmap: (height " + b_.getHeight() + ")");
			}

			byte[] previewBytes = IOUtility.getBytesFromBitmap(b_, false);
			
			if((Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
				info.guardianproject.iocipher.File preview = new info.guardianproject.iocipher.File(entry.originalHash, "PREVIEW_" + entry.name);
				informaCam.ioService.saveBlob(previewBytes, preview);
				entry.preview = new IAsset(preview.getAbsolutePath());
			} else {
				java.io.File preview = new java.io.File(IOUtility.buildPublicPath(new String [] {entry.originalHash}), "PREVIEW_" + entry.name);
				java.io.File list_view = new java.io.File(IOUtility.buildPublicPath(new String [] {entry.originalHash}), "LIST_VIEW_" + entry.name);
				
				try {
					informaCam.ioService.saveBlob(previewBytes, preview, true);
					informaCam.ioService.saveBlob(previewBytes, list_view, true);
				} catch (IOException e) {
					Logger.e(LOG, e);
				}
				
				entry.preview = new IAsset(preview.getAbsolutePath());
				entry.list_view = new IAsset(list_view.getAbsolutePath());
			}

			previewBytes = null;

			if(b == null) {
				b = ImageUtility.createThumb(b_, new int[] {entry.exif.width, entry.exif.height});
			}

			b_.recycle();
			mmr.release();
		} else if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
			InputStream is = informaCam.ioService.getStream(entry.fileAsset.path, entry.fileAsset.source);

			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = 8;

			b = BitmapFactory.decodeStream(is, null, opts);
			
		}

		if(b != null) {
			String tPath = entry.name.substring(entry.name.lastIndexOf("."));
			String thumbnailFileName = entry.name.replace(tPath, "_thumb.jpg");
			
			if((Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
				info.guardianproject.iocipher.File thumbnail = new info.guardianproject.iocipher.File(entry.originalHash, thumbnailFileName);
				informaCam.ioService.saveBlob(IOUtility.getBytesFromBitmap(b, 50), thumbnail);
				entry.thumbnail = new IAsset(thumbnail.getAbsolutePath());
			} else {
				java.io.File thumbnail = new java.io.File(IOUtility.buildPublicPath(new String [] {entry.originalHash}), thumbnailFileName);
				Logger.d(LOG, "THUMBNAIL PLACED AT: ");
				Logger.d(LOG, thumbnail.getAbsolutePath());
				try {
					informaCam.ioService.saveBlob(IOUtility.getBytesFromBitmap(b, 50), thumbnail, true);
				} catch (IOException e) {
					Logger.d(LOG, "WHAAAT?");
					Logger.e(LOG, e);
				}
				
				entry.thumbnail = new IAsset(thumbnail.getAbsolutePath());
			}
			
			b.recycle();			
		}
	}

	private void analyze() throws IOException, NoSuchAlgorithmException, JSONException {
		java.io.File file = new java.io.File(entry.fileAsset.path);

		if(!entry.isAvailable()) {
			entry = null;
			return;
		}
		
		entry.name = file.getName();
		entry.fileAsset.name = entry.name;
		entry.size = file.length();
		entry.timeCaptured = file.lastModified();	// Questionable...?

		entry.originalHash = MediaHasher.hash(file, "SHA-1");

		if(entry.uri == null) {
			entry.uri = IOUtility.getUriFromFile(informaCam, Uri.parse(entry.authority), file).toString();
		}

		Logger.d(LOG, "analyzing: " + entry.asJson().toString());

		// Make folder for assets according to preferences
		if(!entry.mediaType.equals(Models.IDCIMEntry.THUMBNAIL)) {
			
			if((Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
				info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(entry.originalHash);
				try {
					if(!rootFolder.exists()) {
						rootFolder.mkdir();
					}
				} catch(ExceptionInInitializerError e) {
					Logger.e(LOG, e);
				}
			} else {
				java.io.File rootFolder = new java.io.File(Storage.EXTERNAL_DIR, entry.originalHash);
				try {
					if(!rootFolder.exists()) {
						rootFolder.mkdir();
					}
				} catch(ExceptionInInitializerError e) {
					Logger.e(LOG, e);
				}
			}
			
			parseExif();
			parseThumbnails();
			commit();
		}		
	}	

	protected void commit() {
		//XXX: get preference here, save and delete original if encryptOriginals
		if((Boolean) informaCam.user.getPreference(IUser.ASSET_ENCRYPTION, false)) {			
			try {
				entry.fileAsset.copy(Storage.Type.FILE_SYSTEM, Storage.Type.IOCIPHER, entry.originalHash);
			} catch (IOException e) {
				Logger.e(LOG, e);
			}
		}
		
		
	}
}