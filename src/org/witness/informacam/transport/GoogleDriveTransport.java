package org.witness.informacam.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.organizations.IOrganization;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.ITransportStub.GoogleDrive;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableNotifiedException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class GoogleDriveTransport extends Transport {
	GDSubmissionPermission permission;
	String fileId = null;
	AuthToken authToken = null;
	
	int auth_attempts = 0;

	public GoogleDriveTransport() {
		super(Models.ITransportStub.GoogleDrive.TAG);
	}

	@Override
	protected boolean init() {
		//Following lines make debugging Google permission easier. Make sure that
		//the service account permission is revoked (on your google account "security"
		//settings page) and then uncomment the following lines to remove the local
		//cached token.
		//authToken = new AuthToken(AccountManager.get(informaCam).getAccounts()[0]);
		//if(authToken.token != null)
		//	GoogleAuthUtil.invalidateToken(getApplicationContext(), authToken.token);

		// authenticate google drive
		authToken = new AuthToken(AccountManager.get(informaCam).getAccounts()[0]);
		if(authToken.token != null) {
			// TODO: if user uses tor
			if(!super.init(false)) {
				return false;
			}
			
			// upload to drive, on success: file id is in there
			mBuilder.setProgress(100, 0, false);
			mNotifyManager.notify(0, mBuilder.build());
		

			try {
				
				JSONObject subResponse = (JSONObject) doPost(new GDSubmissionMetadata(), transportStub.asset, GoogleDrive.Urls.UPLOAD);
				if(subResponse != null) {
			
						fileId = subResponse.getString("id");
						// share to our google drive person
						subResponse = (JSONObject) doPost(new GDSubmissionPermission(), String.format(GoogleDrive.Urls.SHARE, fileId));
						Logger.d(LOG, "CONFIRM:\n" + transportStub.lastResult);
						mBuilder.setProgress(100, 60, false);
						mNotifyManager.notify(0, mBuilder.build());
						
						if(subResponse != null) {
						
							Logger.d(LOG, "CONFIRM:\n" + transportStub.lastResult);
							mBuilder
								.setContentText("Successful upload to: " + repository.asset_root)
								.setTicker("Successful upload to: " + repository.asset_root);
							mBuilder.setAutoCancel(true);
							mBuilder.setProgress(0, 0, false);
							mNotifyManager.notify(0, mBuilder.build());
							finishSuccessfully();
						}
					
				}
				
			
			} catch (Exception e) {
				Logger.e(LOG, e);
				
				if(auth_attempts >= 10) {
					finishUnsuccessfully();
				
					return false;
				}
				
				return init();
			}
		
			
		} else {
			Logger.d(LOG, "AUTH TOKEN NULL-- WHAT TO DO?");
			GoogleAuthUtil.invalidateToken(getApplicationContext(), authToken.token);
			auth_attempts++;
			
			if(auth_attempts >= 10) {
				return false;
			}
			
			return init();
		}
		return true;
	}
	
	@Override
	public Object parseResponse(InputStream response) {
		super.parseResponse(response);
		try {
			response.close();
		} catch (IOException e) {
			Logger.e(LOG, e);
		}

		try {
			return (JSONObject) new JSONTokener(transportStub.lastResult).nextValue();
		} catch (JSONException e) {
			Logger.e(LOG, e);
		}

		Logger.d(LOG, "THIS POST DID NOT WORK");
		return null;
	}

	@Override
	protected HttpURLConnection buildConnection(String urlString, boolean useTorProxy) {
		HttpURLConnection http = super.buildConnection(urlString, useTorProxy);
		http.setRequestProperty("Authorization", "Bearer " + authToken.token);

		return http;
	}

	public static class GoogleDriveEventBroadcaster extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			
			Logger.d(LOG, intent.getAction());
			Bundle b = intent.getExtras();
			if (b != null)
			{
				for(String k : b.keySet()) {
					Logger.d(LOG, k);
					if(k.equals(Actions.USER_ACCEPT_ACTION)) {
						for(IOrganization organization : InformaCam.getInstance().installedOrganizations.organizations) {
							InformaCam.getInstance().resendCredentials(organization);
						}
					}
				}
			}

		}

	}

	public class GDSubmissionMetadata extends Model implements Serializable {
		private static final long serialVersionUID = -5854206953634303757L;

		public String title;

		public GDSubmissionMetadata() {
			super();
			
			title = GoogleDriveTransport.this.transportStub.asset.assetName;
		}

		public GDSubmissionMetadata(GDSubmissionMetadata metadata) {
			super();
			inflate(metadata);
		}
	}

	public class GDSubmissionPermission extends Model implements Serializable {
		private static final long serialVersionUID = 2781623454711408251L;

		public String type;
		public String role;
		public String value;

		public GDSubmissionPermission() {
			super();
			role = Models.ITransportStub.GoogleDrive.Roles.WRITER;
			type = Models.ITransportStub.GoogleDrive.Permissions.USER;
			value = GoogleDriveTransport.this.transportStub.getRepository(Models.ITransportStub.GoogleDrive.TAG).asset_id;
		}

		public GDSubmissionPermission(GDSubmissionPermission permission) {
			super();
			inflate(permission);
		}
	}
	
	public class AuthToken {
		public Account account;
		public String token = null;

		public Intent userAcceptCallback = new Intent().setAction(Actions.USER_ACCEPT_ACTION);

		public AuthToken(Account account) {
			this.account = account;
			try {
				Logger.d(LOG, "THIS GOOGLE ACCT: " + this.account.name);
				this.token = GoogleAuthUtil.getTokenWithNotification(informaCam, this.account.name, Models.ITransportStub.GoogleDrive.SCOPE, null, userAcceptCallback);
			} catch (UserRecoverableNotifiedException e) {
				Logger.d(LOG, "here we must wait for user to allow us access.");
				Logger.e(LOG, e);
				this.token = null;
			} catch (IOException e) {
				Logger.e(LOG, e);
			} catch (GoogleAuthException e) {
				Logger.e(LOG, e);
			}			
		}
	}
}
