package org.witness.informacam.share;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Properties;

import org.witness.informacam.R;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxLink;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
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
	
	private Context mContext = null;

	private NotificationCompat.Builder mBuilder;
	private final static int NOTIFY_ID = 7777;
	
	private static DropboxSyncManager mInstance;
	private Thread mThread = null;
	
	private DropboxSyncManager (Context context)
	{
		mContext = context;
	}
	
	public static synchronized DropboxSyncManager getInstance (Context context)
	{
		if (mInstance == null)
			mInstance = new DropboxSyncManager (context);
		
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
				mDBApi = new DropboxAPI<AndroidAuthSession>(session);
				
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

		showNotification();
				
		if (mDBApi != null && (mThread == null || (!mThread.isAlive())))
		{

			mThread = new Thread ()
			{
				public void run ()
				{
					File file = null;
				
					int numUploaded = 0;
					
					while ( llFileQ.peek() != null)
					{
						file = llFileQ.pop();
						try {
							Entry result = uploadFile(file);
							if (result != null)
								numUploaded++;
							
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (DropboxException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

					finishNotification(numUploaded);
				}
			};
			
			mThread.start();
		}
	}
	
	@SuppressWarnings("resource")
	private Entry uploadFile (File file) throws DropboxException, FileNotFoundException
	{
		InputStream inputStream = null;
		long fileLength = -1;
		String fileName = null;
		
		if (file.exists())
		{
			inputStream = new FileInputStream(file);
			fileLength = file.length();
			fileName = file.getName();
		}
		else if ((new java.io.File(file.getAbsolutePath())).exists())
		{
			java.io.File newFile = new java.io.File(file.getAbsolutePath());
			//this is an unencrypted file... let's still use it!
			inputStream = new java.io.FileInputStream(newFile);
			fileLength = newFile.length();
			fileName = newFile.getName();
		}
		
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
			Entry response = mDBApi.putFile("/" + fileName, inputStream,
					fileLength, null, null);
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
	
	private void showNotification ()
	{

		    if (mBuilder == null)
		    {
				  Intent intentLaunch = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
					
				    PendingIntent pendingIntent=PendingIntent.getActivity(mContext, 0,
				    		intentLaunch, Intent.FLAG_ACTIVITY_NEW_TASK);

				    mBuilder =new NotificationCompat.Builder(mContext)
			                                .setSmallIcon(R.drawable.ic_action_backup)
			                                .setContentTitle(mContext.getString(R.string.backing_up_camerav_gallery_to_dropbox))
			                                .setContentIntent(pendingIntent);
				    
				    
		    }

		    mBuilder.setContentTitle(mContext.getString(R.string.backing_up_camerav_gallery_to_dropbox));
		    mBuilder.setProgress(0, 100, true);
		    mBuilder.setContentText(mContext.getString(R.string.camerav_syncing_photos_and_videos_));
		    
		    NotificationManager mNotificationManager =
		    	    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		    	// mId allows you to update the notification later on.
		    	mNotificationManager.notify(NOTIFY_ID,mBuilder.build());
		    	        
	}
	
	private void finishNotification (int numUploaded)
	{
		  NotificationManager mNotificationManager =
		    	    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		  
		    String contentText = "CameraV synced " + numUploaded + " new photos and videos.";
		    
		    mBuilder.setContentTitle(mContext.getString(R.string.dropbox_backup_complete));
		    mBuilder.setContentText(contentText);
		    mBuilder.setProgress(100, 100, false);
		    
		    mNotificationManager.notify(NOTIFY_ID,mBuilder.build());
	}
}
