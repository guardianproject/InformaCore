package org.witness.informacam.models.j3m;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.media.IMedia;

public class IIntakeData extends Model {
	public String data = null;
	public String signature = null;
	
	public IIntakeData(IMedia m) {
		InformaCam informaCam = InformaCam.getInstance();
		
		data = m.asJson().toString();
		signature = new String(informaCam.signatureService.signData(data.getBytes()));
	}
}
