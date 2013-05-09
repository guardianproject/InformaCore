package org.witness.informacam.models.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.TimeUtility;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;

import android.util.Log;

public class IMediaManifest extends Model {
	public List<IMedia> media = new ArrayList<IMedia>();

	public IMediaManifest() {}
	
	public void save() {
		InformaCam.getInstance().saveState(((Model) this), new info.guardianproject.iocipher.File(IManifest.MEDIA));
	}
	
	public List getAllByType(String mimeType) {
		List mediaByType = null;
		for(IMedia m : media) {
			if(m.dcimEntry.mediaType.equals(mimeType)) {
				if(mediaByType == null) {
					mediaByType = new ArrayList<IMedia>();
				}
				
				if(mimeType.equals(MimeType.IMAGE)) {
					if(mediaByType == null) {
						mediaByType = new ArrayList<IImage>();
					}
					
					IImage m_ = new IImage(m);
					mediaByType.add(m_);
					continue;
				}
				
				if(mimeType.equals(MimeType.VIDEO)) {
					if(mediaByType == null) {
						mediaByType = new ArrayList<IVideo>();
					}
					
					IVideo m_ = new IVideo(m);
					mediaByType.add(m_);
					continue;
				}
				
				if(mimeType.equals(MimeType.LOG)) {
					if(mediaByType == null) {
						mediaByType = new ArrayList<ILog>();
					}
					
					ILog m_ = new ILog(m);
					mediaByType.add(m_);
					continue;
				}
			}
		}
		
		if(mediaByType == null) {
			Log.d(LOG, "no, media is null");
		}
		
		return mediaByType;
	}
	
	public IMedia getById(String mediaId) {
		for(IMedia m : media) {
			if(m._id.equals(mediaId)) {
				return m;
			}
		}
		
		return null;
	}
	
	public List<IMedia> getByDay(long timestamp, String mimeType) {
		return getByDay(timestamp, mimeType, -1);
	}
	
	public List<IMedia> getByDay(long timestamp) {
		return getByDay(timestamp, null, -1);
	}
	
	public List<IMedia> getByDay(long timestamp, String mimeType, int limit) {
		List<IMedia> media_ = media;
		List<IMedia> matches = null;
		if(mimeType != null) {
			media_ = this.getAllByType(mimeType);
		}
		
		for(IMedia m : media_) {
			//Log.d(LOG, "MEDIA: " + m.asJson().toString());
			boolean doesMatch = false;
			if(!m.dcimEntry.mediaType.equals(MimeType.LOG)) {
				doesMatch = TimeUtility.matchesDay(timestamp, m.dcimEntry.timeCaptured);
			} else {
				doesMatch = TimeUtility.matchesDay(timestamp, ((ILog) m).startTime);
			}
			
			if(doesMatch) {
				if(matches == null) {
					matches = new ArrayList<IMedia>();
				}
				
				matches.add(m);
				if(limit != -1 && matches.size() == limit) {
					break;
				}
			}			
		}
		
		return matches;
	}
	
	public void sortBy(int order) {
		if(media == null || media.size() == 0) {
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
					return m1.dcimEntry.timeCaptured > m2.dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1);
				}
				
			};
			Collections.sort(media, DateDesc);
			break;
		case Models.IMediaManifest.Sort.DATE_ASC:
			Comparator<IMedia> DateAsc = new Comparator<IMedia>() {

				@Override
				public int compare(IMedia m1, IMedia m2) {
					return m1.dcimEntry.timeCaptured < m2.dcimEntry.timeCaptured ? -1 : (m1==m2 ? 0 : 1);
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
