package org.witness.informacam.models;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class IMediaManifest extends Model {
	public List<IMedia> media = null;

	public IMediaManifest() {}
	
	public void save() {
		InformaCam.getInstance().saveState(((Model) this), new info.guardianproject.iocipher.File(IManifest.MEDIA));
	}
	
	public IMedia getById(String mediaId) {
		for(IMedia m : media) {
			if(m._id.equals(mediaId)) {
				return m;
			}
		}
		
		return null;
	}
	
	public void sortBy(int order) {
		if(media == null || media.size() < 0) {
			return;
		}
		
		for(IMedia m : media) {
			try {
				m.put(Models.IMediaManifest.Sort.IS_SHOWING, true);
			} catch (JSONException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		switch(order) {
		case Models.IMediaManifest.Sort.DATE_DESC:
			Comparator<IMedia> DateDesc = new Comparator<IMedia>() {

				@Override
				public int compare(IMedia m1, IMedia m2) {
					return (m1.dcimEntry.timeCaptured > m2.dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1));
				}
				
			};
			Collections.sort(media, DateDesc);
			break;
		case Models.IMediaManifest.Sort.DATE_ASC:
			Comparator<IMedia> DateAsc = new Comparator<IMedia>() {

				@Override
				public int compare(IMedia m1, IMedia m2) {
					return (m1.dcimEntry.timeCaptured < m2.dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1));
				}
				
			};
			Collections.sort(media, DateAsc);
			break;
		case Models.IMediaManifest.Sort.TYPE_PHOTO:
			for(IMedia m : media) {
				if(!m.dcimEntry.mediaType.equals(Models.IMedia.MimeType.IMAGE)) {
					try {
						m.put(Models.IMediaManifest.Sort.IS_SHOWING, false);
					} catch(JSONException e) {
						Log.e(LOG, e.toString());
						e.printStackTrace();
					}
				}
			}
			break;
		case Models.IMediaManifest.Sort.TYPE_VIDEO:
			for(IMedia m : media) {
				if(!m.dcimEntry.mediaType.equals(Models.IMedia.MimeType.VIDEO)) {
					try {
						m.put(Models.IMediaManifest.Sort.IS_SHOWING, false);
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
