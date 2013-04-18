package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class IMediaManifest extends Model {
	public List<IMedia> media = new ArrayList<IMedia>();

	public IMediaManifest() {}
	
	public void save() {
		InformaCam.getInstance().saveState(((Model) this), new info.guardianproject.iocipher.File(IManifest.MEDIA));
	}
	
	public IMedia getById(String mediaId) {
		for(Object m : media) {
			if(((IMedia) m)._id.equals(mediaId)) {
				Log.d(LOG, "this media " + ((IMedia) m).asJson().toString());
				return (IMedia) m;
			}
		}
		
		return null;
	}
	
	public void sortBy(int order) {
		if(media.size() < 0) {
			return;
		}
		
		for(Object m : media) {
			try {
				((IMedia) m).put(Models.IMediaManifest.Sort.IS_SHOWING, true);
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		switch(order) {
		case Models.IMediaManifest.Sort.DATE_DESC:
			Comparator<Object> DateDesc = new Comparator<Object>() {

				@Override
				public int compare(Object m1, Object m2) {
					return (((IMedia) m1).dcimEntry.timeCaptured > ((IMedia) m2).dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1));
				}
				
			};
			Collections.sort(media, DateDesc);
			break;
		case Models.IMediaManifest.Sort.DATE_ASC:
			Comparator<Object> DateAsc = new Comparator<Object>() {

				@Override
				public int compare(Object m1, Object m2) {
					return (((IMedia) m1).dcimEntry.timeCaptured < ((IMedia) m2).dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1));
				}
				
			};
			Collections.sort(media, DateAsc);
			break;
		case Models.IMediaManifest.Sort.TYPE_PHOTO:
			for(Object m : media) {
				if(!((IMedia) m).dcimEntry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
					try {
						((IMedia) m).put(Models.IMediaManifest.Sort.IS_SHOWING, false);
					} catch(JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
			}
			break;
		case Models.IMediaManifest.Sort.TYPE_VIDEO:
			for(Object m : media) {
				if(!((IMedia) m).dcimEntry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
					try {
						((IMedia) m).put(Models.IMediaManifest.Sort.IS_SHOWING, false);
					} catch(JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
			}
			break;
		case Models.IMediaManifest.Sort.LOCATION:
			// TODO
			break;
		}
		
	}
	
	
}
