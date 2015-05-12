package org.witness.informacam.share;

import info.guardianproject.iocipher.File;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.share.www.SimpleWebServer;
import org.witness.informacam.utils.Constants.Models;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import ch.boye.httpclientandroidlib.conn.util.InetAddressUtils;

public class WebShareService extends Service {

	private static SimpleWebServer mServer;
	private File mRoot = new File("/");
	private String mHost = "localhost";
	private int mPort = 9999;
	
	public final static String ACTION_SERVER_START = "start";
	public final static String ACTION_SERVER_STOP = "stop";
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		
	}


	private void initMediaShare ()
	{
		InformaCam informaCam = InformaCam.getInstance();
		int sorting = Models.IMediaManifest.Sort.DATE_DESC;		
		List<IMedia> listMedia = informaCam.mediaManifest.sortBy(sorting);
		mServer.setMedia(this, listMedia);
		
	}
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		if (intent.getAction() != null)
		{
			if (intent.getAction().equals(ACTION_SERVER_START))
			{
				if (intent.hasExtra("host"))
					mHost = intent.getExtras().getString("host");
				
				if (intent.hasExtra("port"))
					mPort = intent.getExtras().getInt("port");
				
				if (intent.hasExtra("root"))
					mRoot = new File(intent.getExtras().getString("root"));
				
				startServer();
				
			}
			else if (intent.getAction().equals(ACTION_SERVER_STOP))
			{
				stopServer();
			}
		}
		
		 return START_STICKY;
	}

	private void startServer ()
	{
		Thread thread = new Thread ()
		{
			
			public void run ()
			{
				mServer = new SimpleWebServer(mHost,mPort,mRoot,false);
				showNotification ();
				initMediaShare();
				
				try {
					mServer.start();
					
		        } catch (IOException ioe) {
		            System.err.println("Couldn't start server:\n" + ioe);
		            System.exit(-1);
		        }
		
			}
		};
		
		thread.start();

	}
	
	private void stopServer ()
	{
		Thread thread = new Thread ()
		{
			
			public void run ()
			{
				stopForeground(true);
				
				try {
					mServer.stop();
		        } catch (Exception ioe) {
		            System.err.println("Couldn't stop server:\n" + ioe);
		            System.exit(-1);
		        }
		
			}
		};
		
		thread.start();

	}
	
	public static boolean isRunning ()
	{
		return (mServer != null && mServer.isAlive());
	}

	public static String getLocalIpAddress(){
		   try {
		       for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();  
		       en.hasMoreElements();) {
		       NetworkInterface intf = en.nextElement();
		           for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
		           InetAddress inetAddress = enumIpAddr.nextElement();
		                if (!inetAddress.isLoopbackAddress()&& InetAddressUtils.isIPv4Address(inetAddress.getHostAddress())) {
		                	
		                	return inetAddress.getHostAddress();
		                
		                
		                }
		           }
		       }
		       } catch (Exception ex) {
		          Log.e("IP Address", ex.toString());
		      }
		      return null;
		}
	
	private void showNotification ()
	{


		  Intent intentLaunch = getPackageManager().getLaunchIntentForPackage(getPackageName());
		
		    PendingIntent pendingIntent=PendingIntent.getActivity(this, 0,
		    		intentLaunch, Intent.FLAG_ACTIVITY_NEW_TASK);

		    Notification notification=new NotificationCompat.Builder(this)
		                                .setSmallIcon(R.drawable.ic_action_backup)
		                                .setContentTitle(getString(R.string.remote_share_activated))
		                                .setContentIntent(pendingIntent)
		                                .setOngoing(true).build();
		    
		    startForeground(992000, notification);
		    	        
	}
}
