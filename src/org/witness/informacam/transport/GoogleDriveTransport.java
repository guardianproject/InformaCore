package org.witness.informacam.transport;

import org.witness.informacam.utils.Constants.Models;

public class GoogleDriveTransport extends Transport {
	
	public GoogleDriveTransport() {
		super(Models.ITransportStub.GoogleDrive.TAG);
	}
	
	@Override
	protected boolean init() {
		if(!super.init()) {
			return false;
		}
		
		// tell current activity to authenticate google drive
		
		return true;
	}
	
	public void pushToGoogleDrive() {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				
				
			}
			
		});
		
		
	}
}
