package org.witness.informacam.transport;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;

import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;

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
	public static final String SCOPE = "oauth2:https://www.googleapis.com/auth/drive";
	
	GDSubmission submission;
	AuthToken authToken = null;
	
	public GoogleDriveTransport() {
		super(Models.ITransportStub.GoogleDrive.TAG);
	}
	
	@Override
	protected boolean init() {
		// does not require tor yet
		if(!super.init(false)) {
			return false;
		}
		
		// authenticate google drive
		authToken = new AuthToken(AccountManager.get(informaCam).getAccounts()[0]);
		if(authToken.token != null) {
			submission = new GDSubmission();
		}
		return true;
	}
	
	@Override
	protected HttpURLConnection buildConnection(String urlString, boolean useTorProxy) {
		HttpURLConnection http = super.buildConnection(urlString, useTorProxy);
		http.setRequestProperty("Authorization", authToken.token);
		
		return http;
	}
	
	public class GoogleDriveEventBroadcaster extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Logger.d(LOG, intent.getAction());
			Bundle b = intent.getExtras();
			for(String k : b.keySet()) {
				Logger.d(LOG, k);
			}
			
		}
		
	}
	
	public class GDSubmission {		
		public GDSubmissionMetadata metadata;
		public GDSubmissionPermission permission;
				
		public GDSubmission() {
			// send metadata and file
			// update permission
		}
		
		public class GDSubmissionMetadata extends Model implements Serializable {
			private static final long serialVersionUID = -5854206953634303757L;
			
			public String title;
			
			public GDSubmissionMetadata() {
				super();
			}
			
			public GDSubmissionMetadata(GDSubmissionMetadata metadata) {
				super();
				inflate(metadata);
			}
		}
		
		public class GDSubmissionPermission extends Model implements Serializable {
			private static final long serialVersionUID = 2781623454711408251L;
			
			public String userName;
			public String role;
			public String userValue;
			
			public GDSubmissionPermission() {
				super();
				
				
			}
			
			public GDSubmissionPermission(GDSubmissionPermission permission) {
				super();
				inflate(permission);
			}
		}
		
	}
	
	public class AuthToken {
		public Account account;
		public String token = null;
		
		public Intent userAcceptCallback = new Intent().setAction(Actions.USER_ACCEPT_ACTION);
		
		public AuthToken(Account account) {
			this.account = account;
			try {
				this.token = GoogleAuthUtil.getTokenWithNotification(informaCam, this.account.name, SCOPE, null, userAcceptCallback);
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
