package org.witness.informacam.models.j3m;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.witness.informacam.utils.BackgroundProcessor;
import org.witness.informacam.utils.BackgroundTask;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.informacam.utils.Constants.Suckers.Geo;
import org.witness.informacam.utils.ImageUtility;
import org.witness.informacam.utils.MediaHasher;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class IDCIMDescriptor extends Model {
	InformaCam informaCam = InformaCam.getInstance();
	
	public List<IDCIMEntry> shortDescription = new ArrayList<IDCIMEntry>();
	
	private BackgroundProcessor queue = null;
	private long startTime = 0L;
	
	private final static String LOG = Storage.LOG;

	public IDCIMDescriptor() {
		startTime = System.currentTimeMillis()/1000;
	}
	
	public void addEntry(Uri authority, boolean isThumbnail) {
		new EntryJob(queue, authority, isThumbnail, startTime);
	}

	public void startSession() {
		queue = new BackgroundProcessor();
		queue.setOnBatchComplete(new BatchCompleteJob(queue));

		new Thread(queue).start();
		Log.d(LOG, "starting dcim session");
		
		/*
		informaCam.mediaManifest.listMedia.clear();
		informaCam.mediaManifest.save();
		*/
	}

	public void stopSession() {
		Log.d(LOG, "saved a dcim descriptor");
		queue.stop();
	}

	@SuppressWarnings("serial")
	private class EntryJob extends BackgroundTask {
		boolean isThumbnail;
		long startTime;
		Uri authority;

		IDCIMEntry entry = new IDCIMEntry();

		public EntryJob(BackgroundProcessor queue, Uri authority, boolean isThumbnail, long startTime) {
			super(queue);
			
			try {
				entry.put(Models.IDCIMEntry.AUTHORITY, authority.toString());
				
				this.isThumbnail = isThumbnail;
				this.startTime = startTime;
				this.authority = authority;
				
				String sortBy = "date_added DESC";

				if(isThumbnail) {
					sortBy = null;
					entry.mediaType = Models.IDCIMEntry.THUMBNAIL;
				}
				
				if(queryContentProvider(sortBy)) {
					if(!isThumbnail) {
						
						IDCIMEntry clone = new IDCIMEntry(entry);
						if(!shortDescription.contains(clone)) {
							shortDescription.add(clone);
						}
					}
					
					queue.put(this);
				}
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (InterruptedException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}	
		}

		@Override
		protected boolean onStart() {
			
			try
			{
				entry = analyze();
			
				if(entry != null) {
					if(!isThumbnail) {
						IMedia media = new IMedia();
						media.dcimEntry = entry;
						media._id = media.generateId(entry.originalHash);
						
						boolean isFinishedProcessing = false;
	
						if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
							IImage image = new IImage(media);
	
							if(image.analyze()) {
								this.informaCam.mediaManifest.addMediaItem(image);
								isFinishedProcessing = true;
							}
						} else if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
							IVideo video = new IVideo(media);
	
							if(video.analyze()) {
								this.informaCam.mediaManifest.addMediaItem(video);
								isFinishedProcessing = true;
							}
						}
						
						if(isFinishedProcessing) {
							Bundle data = new Bundle();
							data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.DCIM.ADD);
							data.putString(Codes.Extras.CONSOLIDATE_MEDIA, entry.originalHash);
							
							Message message = new Message();
							message.setData(data);
	
							InformaCamEventListener mListener = this.informaCam.getEventListener();
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
				Log.e(LOG,"unable to analyze() new media entry",e);
			}
			

			return super.onStart();
		}

		private boolean queryContentProvider(String sortBy) {
			Cursor cursor = informaCam.getContentResolver().query(authority, null, null, null, sortBy);

			if(cursor != null && cursor.moveToFirst()) {
				entry.fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DATA));

				if(!isThumbnail) {
					entry.timeCaptured = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns.DATE_ADDED));
					entry.mediaType = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.MIME_TYPE));

					if(entry.mediaType.equals(MimeType.VIDEO_3GPP)) {
						entry.mediaType = MimeType.VIDEO;
					}
					
					if(entry.timeCaptured < this.startTime) {
						Log.d(LOG, "this media occured now");
						return false;
					}
					
				}

				entry.id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID));
				cursor.close();

				return true;
			}

			return false;
		}

		private IDCIMEntry analyze() throws IOException, NoSuchAlgorithmException, JSONException {
			
			Log.d(LOG, "analyzing: " + entry.asJson().toString());

			java.io.File file = new java.io.File(entry.fileName);

			entry.name = file.getName();
			entry.size = file.length();
			entry.timeCaptured = file.lastModified();
			
			if(!entry.isAvailable()) {
				return null;
			}

			entry.originalHash = MediaHasher.hash(file, "SHA-1");

			if(entry.uri == null) {
				entry.uri = IOUtility.getUriFromFile(this.informaCam, Uri.parse(entry.getString(Models.IDCIMEntry.AUTHORITY)), file).toString();
			}
		

			if(!entry.mediaType.equals(Models.IDCIMEntry.THUMBNAIL)) {
				boolean getThumbnailFromMediaMetadata = false;
				boolean bruteForceThumbnailFromMedia = false;

				Bitmap b = null;
				
				if(entry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
					getThumbnailFromMediaMetadata = true;
					Log.d(LOG, "definitely getting THUMBNAIL from METADATA RESOURCE");
								
					b = MediaStore.Images.Thumbnails.getThumbnail(this.informaCam.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MICRO_KIND, null);
					if(b == null) {
						b = MediaStore.Images.Thumbnails.getThumbnail(this.informaCam.getContentResolver(), entry.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
					}
				}

				if(entry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
					bruteForceThumbnailFromMedia = true;
				}

				entry.exif = new IExif();

				if (informaCam.informaService != null && informaCam.informaService._geo != null)
				{
					ILogPack logpack = ((GeoSucker) this.informaCam.informaService._geo).forceReturn();
					
					if (logpack != null)
					{
						try {
							entry.exif.location = Model.parseJSONAsFloatArray(logpack.getString(Geo.Keys.GPS_COORDS));
						} catch (JSONException e) {
							Log.e(LOG, e.getMessage(),e);
							
						} catch(NullPointerException e) {
							Log.e(LOG, e.getMessage(), e);
							
							entry.exif.location = new float[] {0f,0f};
						}
					}
					else
					{

						entry.exif.location = new float[] {0f,0f};
					}
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
						Log.e(LOG, e.toString(),e);
					}

					if(bruteForceThumbnailFromMedia) {
						
						//byte[] bytes = this.informaCam.ioService.getBytes(entry.fileName, Type.FILE_SYSTEM);
						//Bitmap b_ = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
						
						InputStream is = informaCam.ioService.getStream(entry.fileName, Type.FILE_SYSTEM);
						
						BitmapFactory.Options opts = new BitmapFactory.Options();
						opts.inSampleSize = 8;
						
						b = BitmapFactory.decodeStream(is, null, opts);
					//	b = ImageUtility.createThumb(b_, new int[] {b_.getWidth(), b_.getHeight()});
					//	entry.exif.width = b.getWidth();
					//	entry.exif.height = b.getHeight();
					//	b_.recycle();


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
						
						
						this.informaCam.ioService.saveBlob(previewBytes, preview);
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

				//TODO this is bad, and should be an async task
				commit();
			}

			return entry;
		}		

		private void commit() {
			// delete/encrypt/replace all the data
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
				Log.e(LOG,"Something is wrong with IOCipher storage - could not save new blobs",e);
			}
		}
	}

	@SuppressWarnings("serial")
	private class BatchCompleteJob extends BackgroundTask {
		List<IDCIMEntry> thumbnails = new ArrayList<IDCIMEntry>();

		public BatchCompleteJob(BackgroundProcessor queue) {
			super(queue);
		}
		
		@Override
		protected boolean onStart() {
			
			persist();
			
			return super.onStart();
		}

		@Override
		protected void onStop() {
			super.onStop();
			
			Bundle data = new Bundle();
			data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.DCIM.STOP);
			Message message = new Message();
			message.setData(data);

			InformaCamEventListener mListener = this.informaCam.getEventListener();
			if (mListener != null) {
				mListener.onUpdate(message);
			}
		}
		
		public void addThumbnail(IDCIMEntry thumbnail) {
			thumbnails.add(thumbnail);
		}

		public void persist() {
			Log.d(LOG, "CLEANING UP AFTER DCIM OBSERVER");
			for(IDCIMEntry entry : thumbnails) {
				this.informaCam.ioService.delete(entry.fileName, Type.FILE_SYSTEM);
				this.informaCam.ioService.delete(entry.uri, Type.CONTENT_RESOLVER);
			}
			
			this.informaCam.ioService.clear(Storage.REVIEW_DUMP, Type.IOCIPHER);
		}
	}
}
