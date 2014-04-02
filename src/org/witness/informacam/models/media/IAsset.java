package org.witness.informacam.models.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models.IUser;

@SuppressWarnings("serial")
public class IAsset extends Model implements Serializable {
	public String path = null;
	public String name = null;
	public int source = Storage.Type.IOCIPHER;
	
	public IAsset() {
		super();
	}
	
	public IAsset(int source) {
		super();
		this.source = source;
	}
	
	public IAsset(String path) {
		super();
		this.path = path;
		
		if((Boolean) InformaCam.getInstance().user.getPreference(IUser.ASSET_ENCRYPTION, false)) {
			this.source = Storage.Type.IOCIPHER;
		} else {
			this.source = Storage.Type.FILE_SYSTEM;
		}
	}
	
	public IAsset(String path, int source) {
		super();
		
		this.path = path;
		this.source = source;
	}
	
	public IAsset(String path, int source, String name) {
		super();
		
		this.path = path;
		this.source = source;
		this.name = name;
		
	}
	
	public IAsset(IAsset asset) {
		super();
		inflate(asset.asJson());
	}
	
	public IAsset(JSONObject asset) {
		super();
		inflate(asset);
	}
	
	public boolean copy(int fromSource, int toSource, String rootFolder) throws IOException {
		return copy(fromSource, toSource, rootFolder, true);
	}
	
	public boolean copy(int fromSource, int toSource, String rootFolder, boolean copyStreams) throws IOException {
		InformaCam informaCam = InformaCam.getInstance();
		Logger.d(LOG, "COPYINGING " + path + " from " + fromSource + " to " + toSource);
		
		String oldPath = path;
		String newPath = IOUtility.buildPath(new String[] { rootFolder, name });
		if(toSource == Type.FILE_SYSTEM) {
			newPath = IOUtility.buildPath(new String[] { newPath });
		}
		
		if(copyStreams) {
			InputStream is = informaCam.ioService.getStream(oldPath, fromSource);
			if(is == null || !informaCam.ioService.saveBlob(is, new IAsset(newPath, toSource))) {
				return false;
			}
		}
		
		source = toSource;
		path = newPath;
		return true;
	}

}