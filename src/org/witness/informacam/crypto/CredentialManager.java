package org.witness.informacam.crypto;

import info.guardianproject.cacheword.CacheWordActivityHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.cacheword.PassphraseSecrets;
import info.guardianproject.cacheword.Wiper;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.organizations.ICredentials;
import org.witness.informacam.utils.Constants.App.Crypto;
import org.witness.informacam.utils.Constants.Actions;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class CredentialManager implements ICacheWordSubscriber {
	protected CacheWordActivityHandler cacheWord;
	
	private int status;
	private boolean initIOCipher = true;
	private Context context;
	
	protected boolean firstUse = false;
	InformaCam informaCam = InformaCam.getInstance();
	private final static String LOG = Crypto.LOG;
	
	public CredentialManager(Context context, boolean initIOCipher) {
		this(context, initIOCipher, false);
	}
	
	public CredentialManager(Context context, boolean initIOCipher, boolean firstUse) {
		this.status = Codes.Status.UNKNOWN;
		this.initIOCipher = initIOCipher;
		this.context = context;
		this.firstUse = firstUse;
		
		cacheWord = new CacheWordActivityHandler(this.context, this);
	}
	
	public boolean login(String password) {
        PassphraseSecrets secrets;
        try {
            secrets = PassphraseSecrets.fetchSecrets(context, password.toCharArray());
            cacheWord.setCachedSecrets(secrets);
            
            
            return true;
        } catch (GeneralSecurityException e) {
            Log.e(LOG, "invalid password or secrets has been tampered with");
            e.printStackTrace();
        }
		
		return false;
	}
	
	public boolean logout() {
		cacheWord.manuallyLock();
		return true;
	}
	
	public int getStatus() {
		if(status != Codes.Status.UNKNOWN) {
			return status;
		} else {
			if(!cacheWord.isLocked()) {
				status = Codes.Status.UNLOCKED;
			} else {
				if(cacheWord.getCachedSecrets() == null) {
					status = Codes.Status.UNINITIALIZED;
				} else {
					status = Codes.Status.LOCKED;
				}
			}
			
			return status;
		}
	}
	
	public void setMasterPassword(String password) {
		PassphraseSecrets secret = PassphraseSecrets.initializeSecrets(context, password.toCharArray());
		cacheWord.setCachedSecrets(secret);
		Log.d(LOG, "set cacheword secret\nalgo: " + secret.getSecretKey().getAlgorithm() + "\n:format: " + secret.getSecretKey().getFormat());
	}
	
	public byte[] setAuthToken(String authToken) {
		SecretKey key = ((PassphraseSecrets) cacheWord.getCachedSecrets()).getSecretKey();
		return AesUtility.EncryptToKey(key, authToken).getBytes(Wiper.Utf8CharSet);
	}
	
	private void update(int code) {
		Bundle data = new Bundle();
		data.putInt(Codes.Extras.MESSAGE_CODE, code);
		Intent intent = new Intent(Actions.INFORMACAM_START)
			.putExtra(Codes.Keys.SERVICE, data)
			.putExtra(Codes.Extras.RESTRICT_TO_PROCESS, informaCam.getProcess());
		informaCam.sendBroadcast(intent);
	}
	
	public void onPause() {
		cacheWord.onPause();
	}
	
	public void onResume() {
		cacheWord.onResume();
	}
	
	@Override
	public void onCacheWordUninitialized() {
		Log.d(LOG, "onCacheWordUninitialized()");
		this.status = Codes.Status.UNINITIALIZED;
	}

	@Override
	public void onCacheWordLocked() {
		Log.d(LOG, "onCacheWordLocked()");
		
		informaCam.user.isLoggedIn = false;
		informaCam.user.lastLogOut = System.currentTimeMillis();
		
		informaCam.saveState(informaCam.user);
		this.status = Codes.Status.LOCKED;
		
	}

	@Override
	public void onCacheWordOpened() {
		Log.d(LOG, "onCacheWordOpened()");
		cacheWord.setTimeoutMinutes(-1);
		
		boolean hasIOCipher = !initIOCipher;
		
		if(initIOCipher) {
			ICredentials credentials = new ICredentials();
			credentials.inflate(informaCam.ioService.getBytes(Models.IUser.CREDENTIALS, Type.INTERNAL_STORAGE));
			
			SecretKey key = ((PassphraseSecrets) cacheWord.getCachedSecrets()).getSecretKey();
			byte[] authTokenBytes = AesUtility.DecryptWithKey(key, credentials.iv.getBytes(), credentials.passwordBlock.getBytes());
			String authToken = new String(authTokenBytes, Wiper.Utf8CharSet);
			
			if(authToken != null && informaCam.ioService.initIOCipher(authToken)) {
				hasIOCipher = true;
			} else {
				Log.e(LOG, "COULD NOT FULLY OPEN IOCIPHER AND GET CREDENTIALS AND STUFF");
			}
		}
		
		if(hasIOCipher) {
			informaCam.initData();
			informaCam.user.inflate(informaCam.ioService.getBytes(IManifest.USER, Type.INTERNAL_STORAGE));
			
			informaCam.user.isLoggedIn = true;
			informaCam.user.lastLogIn = System.currentTimeMillis();
			
			try
			{
				informaCam.ioService.saveBlob(informaCam.user.asJson().toString().getBytes(), new java.io.File(IManifest.USER));
				
				this.status = Codes.Status.UNLOCKED;
				update(Codes.Messages.Home.INIT);
			}
			catch (IOException ioe)
			{
				Log.e(LOG,"iocipher saveState() error",ioe);
			}
		}
	}
}
