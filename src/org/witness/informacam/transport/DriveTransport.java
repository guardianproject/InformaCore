package org.witness.informacam.transport;

import java.io.IOException;
import java.util.Arrays;

import org.witness.informacam.utils.Constants.Models;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

public class DriveTransport extends Transport {
	Drive service;
	GoogleAccountCredential credentials;
		
	private static final int REQUEST_ACCOUNT_PICKER = 1;
	private static final int REQUEST_AUTHORIZATION = 2;

	@Override
	protected void init() {
		credentials = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
		startActivityForResult(credentials.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
	}
	
	@Override
	protected void send() {
		sendToDrive();
	}
	
	private void sendToDrive() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				File file = new File();
				file.setTitle(transportStub.assetName);
				file.setMimeType(transportStub.mimeType);
				file.setParents(Arrays.asList(new ParentReference().setId(transportStub.getAssetRootOfRepository(Models.ITransportStub.RepositorySources.GOOGLE_DRIVE))));
				
				java.io.File file_content = new java.io.File(transportStub.assetPath);
				FileContent media_content = new FileContent(transportStub.mimeType, file_content);
				
				try {
					File resulting_file = service.files().insert(file, media_content).execute();
					Log.d(LOG, "AWESOME SAUCE:\n" + resulting_file.toPrettyString());

					finishSuccessfully();
				} catch(UserRecoverableAuthIOException e) {
					Log.e(LOG, "AUTH IO ExCEPTION!");
					Log.e(LOG, e.toString());
					e.printStackTrace();
					
					Log.d(LOG, e.getIntent().toUri(0));
					startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
				} catch (IOException e) {
					Log.e(LOG, "REGULAR IO Exception");
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}
			}
		}).start();
		
	}
	
	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch(requestCode) {
		case REQUEST_ACCOUNT_PICKER:
			if(resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
				String account_name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				if(account_name != null) {
					credentials.setSelectedAccountName(account_name);
					service = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credentials).build();
					Log.d(LOG, "SERVICE INITIATED!");
				}
			}
			break;
		case REQUEST_AUTHORIZATION:
			if(resultCode == Activity.RESULT_OK) {
				Log.d(LOG, "REQUEST AUTH OK");
			} else {
				Log.d(LOG, "BAD AUTH. REQUESTING AUTH...");
				init();
			}
			
		}
	}
	
}
