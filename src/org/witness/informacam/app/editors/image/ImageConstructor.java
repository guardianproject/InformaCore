package org.witness.informacam.app.editors.image;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.app.editors.image.filters.CrowdPixelizeObscure;
import org.witness.informacam.app.editors.image.filters.InformaTagger;
import org.witness.informacam.app.editors.image.filters.PixelizeObscure;
import org.witness.informacam.app.editors.image.filters.SolidObscure;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.App.ImageEditor;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;

import com.google.common.cache.LoadingCache;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;

public class ImageConstructor {
	static {
		System.loadLibrary("JpegRedaction");
	}
	
	public static native int constructImage(
			String originalImageFilename, 
			String informaImageFilename, 
			String metadataObjectString, 
			int metadataLength);
	
	public static native byte[] redactRegion(
			String originalImageFilename,
			String informaImageFilename,
			int left,
			int right,
			int top,
			int bottom,
			String redactionCommand);
	
	public ImageConstructor(LoadingCache<Long, LogPack> annotationCache) {						
		try {
			// XXX: create clone as flat file -- should not need to do this though :(
			Uri originalUri = InformaService.getInstance().originalUri;
			java.io.File clone = IOCipherService.getInstance().moveFromIOCipherToMemory(originalUri, originalUri.getLastPathSegment()); 
						
			// get all image regions and run through image constructor
			List<Entry<Long, LogPack>> annotations = InformaService.getInstance().getAllEventsByTypeWithTimestamp(CaptureEvent.REGION_GENERATED, annotationCache);
			
			for(Entry<Long, LogPack> entry : annotations) {
				LogPack lp = entry.getValue();
				Log.d(Storage.LOG, lp.toString());
				
				String redactionMethod = lp.getString(Constants.Informa.Keys.Data.ImageRegion.FILTER);
				if(!redactionMethod.equals(InformaTagger.class.getName())) {
					String redactionCode = "";
					
					if(redactionMethod.equals(PixelizeObscure.class.getName()))
						redactionCode = ImageEditor.Filters.PIXELIZE;
					else if(redactionMethod.equals(SolidObscure.class.getName()))
						redactionCode = ImageEditor.Filters.SOLID;
					else if(redactionMethod.equals(CrowdPixelizeObscure.class.getName()))
						redactionCode = ImageEditor.Filters.CROWD_PIXELIZE;
					
					String regionCoordinates = lp.getString(Constants.Informa.Keys.Data.ImageRegion.COORDINATES);
					
					int left = (int) Float.parseFloat(regionCoordinates.substring(regionCoordinates.indexOf(",") + 1, regionCoordinates.length() - 1));
					int top = (int) Float.parseFloat(regionCoordinates.substring(1, regionCoordinates.indexOf(",")));
					int right = (int) (left + Float.parseFloat(lp.getString(Constants.Informa.Keys.Data.ImageRegion.WIDTH)));
					int bottom = (int) (top + Float.parseFloat(lp.getString(Constants.Informa.Keys.Data.ImageRegion.HEIGHT)));
					
					Log.d(App.LOG, "top: " + top + " left: " + left + " right " + right + " bottom " + bottom + " redaction " + redactionCode);
					
					byte[] redactionPack = redactRegion(clone.getAbsolutePath(), clone.getAbsolutePath(), left, right, top, bottom, redactionCode);
										
					JSONObject imageRegionData = new JSONObject();
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.LENGTH, redactionPack.length);
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.HASH, MediaHasher.hash(redactionPack, "SHA-1"));
					imageRegionData.put(Constants.Informa.Keys.Data.ImageRegion.BYTES, Base64.encode(redactionPack, Base64.DEFAULT));
					
					lp.put(Constants.Informa.Keys.Data.ImageRegion.UNREDACTED_DATA, imageRegionData);
				}
			}
			
			if(InformaService.getInstance().informa.addToAnnotations(annotations)) {
				// then it is ok... time to encrypt
				// when done, for each in the encrypt list, insert metadata
				
				/* TODO HERE!
				(CaptureEvent.Keys.TYPE, CaptureEvent.MEDIA_SAVED);
				*/
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {}
		
		
		// add to upload queue if possible
		
		
	}
}