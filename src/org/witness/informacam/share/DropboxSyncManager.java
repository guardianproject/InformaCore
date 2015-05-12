package org.witness.informacam.share;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import android.app.Activity;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxLink;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

public class DropboxSyncManager {

	final static private String APP_KEY = "j0ioje8k7q5o6vc";
	final static private String APP_SECRET = "f5s6qyuwzq076yk";
	
	// In the class declaration section:
	private DropboxAPI<AndroidAuthSession> mDBApi;
    private AndroidAuthSession mSession;

	private boolean isAuthenticating = false;
	
	private String mStoredAccessToken;
	
	private LinkedList<File> llFileQ;
	
	private static DropboxSyncManager mInstance;
	
	private DropboxSyncManager ()
	{
		
	}
	
	public static synchronized DropboxSyncManager getInstance ()
	{
		if (mInstance == null)
			mInstance = new DropboxSyncManager ();
		
		return mInstance;
	}
	
	public boolean init (Activity a)
	{
		if (mDBApi == null)
		{
			loadCredentials();
			
			if (mStoredAccessToken == null)
			{
				// And later in some initialization function:
				AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
				AndroidAuthSession session = new AndroidAuthSession(appKeys);
				mDBApi = new DropboxAPI<AndroidAuthSession>(session);
				isAuthenticating = true;
				authenticate(a);
				
				return false;
			}
			else
			{
				AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
				AndroidAuthSession session = new AndroidAuthSession(appKeys);
				session.setOAuth2AccessToken(mStoredAccessToken);
				
				return session.isLinked();
			}
		}
		
		return true;
	}
	
	public synchronized void uploadFileAsync (File file) 
	{
		if (llFileQ == null)
			llFileQ = new LinkedList<File>();
		
		llFileQ.add(file);
		
		if (mDBApi != null)
		{
			new Thread ()
			{
				public void run ()
				{
					File file = null;
				
					while ( llFileQ.peek() != null)
					{
						file = llFileQ.pop();
						try {
							uploadFile(file);
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (DropboxException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}.start();
		}
	}
	
	@SuppressWarnings("resource")
	private Entry uploadFile (File file) throws DropboxException, FileNotFoundException
	{
		FileInputStream inputStream = new FileInputStream(file);
		
		String remotePath = "/" + file.getName();
		
		boolean remoteExists = false;
		
		try
		{
			DropboxLink dl = mDBApi.media(remotePath, true);
			if (dl != null)
				remoteExists = true;
			
		}
		catch (Exception e)
		{
			remoteExists = false;
		}
		
		if (!remoteExists)
		{	
			Entry response = mDBApi.putFile("/" + file.getName(), inputStream,
			                                file.length(), null, null);
			return response;
		//	Log.i("DbExampleLog", "The uploaded file's rev is: " + response.rev);
		}
		
		return null;
	}
	
	private void authenticate (Activity a)
	{
		mSession = mDBApi.getSession();
		
		// MyActivity below should be your activity class name
		mSession.startOAuth2Authentication(a);
	}
	
	public void finishAuthentication ()
	{
		
		if (mDBApi != null && isAuthenticating)
		{
			mSession = mDBApi.getSession();
	
			if (mSession.authenticationSuccessful()) {
		        try {
		            // Required to complete auth, sets the access token on the session
		        	mSession.finishAuthentication();
			           
		            
		            saveCredentials();
		    
		            isAuthenticating = false;
		            
		        } catch (IllegalStateException e) {
		            Log.e("DbAuthLog", "Error authenticating", e);
		        }
		        catch (IOException e) {
		            Log.e("DbAuthLog", "Error I/O", e);
		        }
		    }
		}

	}
	
	private boolean loadCredentials ()
	{
		try
		{
			Properties props = new Properties();
			info.guardianproject.iocipher.File fileProps = new info.guardianproject.iocipher.File("/dropbox.properties");
			
			if (fileProps.exists())
			{
		        info.guardianproject.iocipher.FileInputStream fis = new info.guardianproject.iocipher.FileInputStream(fileProps);        
		        props.loadFromXML(fis);
		        
		        mStoredAccessToken = props.getProperty("dbtoken");
		        
		        return true;
			}
	        
	        
		}
		catch (IOException ioe)
		{
			Log.e("DbAuthLog", "Error I/O", ioe);
		}
		
		return false;
	}
	
	private void saveCredentials () throws IOException
	{
		
		if (mSession != null && mSession.isLinked()
				&& mSession.getOAuth2AccessToken() != null)
		{
			
	        mStoredAccessToken = mSession.getOAuth2AccessToken();
	        
	        Properties props = new Properties();
	        props.setProperty("dbtoken", mStoredAccessToken);
	        
	        info.guardianproject.iocipher.File fileProps = new info.guardianproject.iocipher.File("/dropbox.properties");
	        info.guardianproject.iocipher.FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileProps);
	        props.storeToXML(fos,"");
	        fos.close();
	        
		}
		else
		{
			Log.d("Dropbox","no valid dropbox session / not linked");
	        
		}
	}
}
