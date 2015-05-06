package org.witness.informacam.share;

import info.guardianproject.iocipher.File;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import fi.iki.elonen.SimpleWebServer;

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


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
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
		
		return super.onStartCommand(intent, flags, startId);
	}

	private void startServer ()
	{
		Thread thread = new Thread ()
		{
			
			public void run ()
			{
				mServer = new SimpleWebServer(mHost,mPort,mRoot,false);
				
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
		                if (!inetAddress.isLoopbackAddress()) {
		                return inetAddress.getHostAddress().toString();
		                }
		           }
		       }
		       } catch (Exception ex) {
		          Log.e("IP Address", ex.toString());
		      }
		      return null;
		}
}