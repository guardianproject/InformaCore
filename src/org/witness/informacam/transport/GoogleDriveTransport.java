package org.witness.informacam.transport;

import java.io.IOException;
import java.util.Arrays;

import org.witness.informacam.utils.ActivityPipe;
import org.witness.informacam.utils.ActivityPipe.PipeRunnable;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.accounts.AccountManager;
import android.app.Activity;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

public class GoogleDriveTransport extends Transport {
	Drive service;
	GoogleAccountCredential credentials;
	
	public GoogleDriveTransport() {
		super(Models.ITransportStub.GoogleDrive.TAG);
	}
	
	@Override
	protected boolean init() {
		if(!super.init()) {
			return false;
		}
		
		// tell current activity to authenticate google drive
		initDriveClient();
		return true;
	}
	
	private void initDriveClient() {
		credentials = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
		
		ActivityPipe activityPipe = new ActivityPipe(credentials.newChooseAccountIntent(), Codes.Authentication.REQUEST_ACCOUNT_PICKER);
		
		synchronized(this) {
			activityPipe.setPipeRunnable(new PipeRunnable(activityPipe) {
				@Override
				public void run() {

					switch(requestCode) {
					case Codes.Authentication.REQUEST_ACCOUNT_PICKER:
						if(responseCode == Activity.RESULT_OK && responseObject != null && responseObject.getExtras() != null) {
							String accountName = responseObject.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
							if(accountName != null) {
								credentials.setSelectedAccountName(accountName);
								service = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credentials).build();
								Logger.d(LOG, "DRIVE SERVICE INITIATED");

								File metadata = new File();
								metadata.setTitle(transportStub.asset.assetName);
								metadata.setMimeType(transportStub.asset.mimeType);
								
								byte[] assetBytes = informaCam.ioService.getBytes(transportStub.asset.assetPath, Type.IOCIPHER);
								ByteArrayContent content = new ByteArrayContent(transportStub.asset.mimeType, assetBytes);
								
								try {
									File upload = service.files().insert(metadata, content).execute();
									if(upload != null) {
										Permission toOrganization = new Permission();
										toOrganization.setType("user");
										toOrganization.setValue(repository.asset_id);
										toOrganization.setRole("reader");
										
										if(service.permissions().insert(upload.getId(), toOrganization).execute() != null) {
											GoogleDriveTransport.this.finishSuccessfully();
										}
									}
								} catch(IOException e) {
									Logger.e(LOG, e);
								}
							}
						}
						break;
					case Codes.Authentication.REQUEST_AUTHORIZATION:
						if(responseCode == Activity.RESULT_OK) {
							Logger.d(LOG, "REQUEST AUTH OK");
						} else {
							Logger.d(LOG, "BAD AUTH.  REQUESTING AUTH AGAIN");
							initDriveClient();
						}
						break;
					}
				}
			});
		}
	}
	
	
	
	public void pushToGoogleDrive() {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				
				
			}
			
		});
		
		
	}
}
