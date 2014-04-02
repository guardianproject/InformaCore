package org.witness.informacam.models.j3m;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;

public class IIntakeData extends Model {
	public String data = null;
	public String signature = null;
	
	public IIntakeData() {
		super();
	}
	
	public IIntakeData(IIntakeData intakeData) {
		super();
		inflate(intakeData);
	}
	
	public IIntakeData(long timeCreated, String timezone, long timeOffset, String originalHash, String cameraComponentPackageName) {
		super();
		InformaCam informaCam = InformaCam.getInstance();
				
		data = "timezone=" + timezone + ";timeCreated=" + timeCreated + ";timeOffset=" + timeOffset +";cameraComponentPackageName=" + cameraComponentPackageName + ";originalHash=" + originalHash;
		signature = new String(informaCam.signatureService.signData(data.getBytes()));
	}
}
