package org.witness.informa.utils.io;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.witness.informa.EncryptActivity;
import org.witness.informa.EncryptActivity.MetadataPack;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

public class Uploader extends Service {
	private final IBinder binder = new LocalBinder();
	private List<MetadataPack> queue;
	boolean isCurrentlyUploading = false;
	public static Uploader uploader;

	public class LocalBinder extends Binder {
		public Uploader getService() {
			return Uploader.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent i) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		Log.d(InformaConstants.TAG, "UPLOADER SERVICE STARTED!");
		queue = new ArrayList<MetadataPack>();
		uploader = this;
		sendBroadcast(new Intent().setAction(Keys.Service.UPLOADER_AVAILABLE));
	}
	
	public static Uploader getUploader() {
		return uploader;
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	public interface MetadataHandler {
		public ArrayList<MetadataPack> setMetadata();
	}
	
	private void getTicket(MetadataPack mp) {
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		
		String url = "https://" + mp.tdDestination +
					"/?device_pgp=" + tm.getDeviceId() + 
					"&bytes_expected=" + new File(mp.filepath).length() +
					"&unredacted_data_hash=" + mp.hash;
		
		Log.d(InformaConstants.TAG, "TICKET URL:\n" + url);
		
		
	}
	
	private void uploadMedia(MetadataPack mp) {
		
	}
	
	private void startUploading() {
		Log.d(InformaConstants.TAG, "STARTING UPLOADING!");
		for(MetadataPack mp : queue) {
			if(mp.authToken == null)
				getTicket(mp);
			
			Log.d(InformaConstants.TAG, mp.toString());
		}
	}
	
	public void addToQueue(MetadataPack mp) {
		queue.add(mp);
		if(!isCurrentlyUploading)
			startUploading();
	}
	
	public void addToQueue(ArrayList<MetadataPack> mp) {
		queue.addAll(mp);
		if(!isCurrentlyUploading)
			startUploading();
	}
	
	private class ConnectionFactory {
		HttpClient httpclient;
		HttpHost proxy;
		String url;
		
		private final HostnameVerifier NO_VER = new HostnameVerifier() {
			public boolean verify(String host, SSLSession session) {
				return true;
			}
		};
		
		public ConnectionFactory(String url) {
			httpclient = new DefaultHttpClient();
			proxy = new HttpHost(url, 8118);
			httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
			this.url = url;
		}
		
		private void trustThisHost() {
			TrustManager[] trustThisCert = new TrustManager[] {
				new X509TrustManager() {

					@Override
					public void checkClientTrusted(X509Certificate[] chain,
							String authType) throws CertificateException {}

					@Override
					public void checkServerTrusted(X509Certificate[] chain,
							String authType) throws CertificateException {}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[] {};
					}
					
				}
			};
			
			try {
				SSLContext sc = SSLContext.getInstance("TLS");
				sc.init(null, trustThisCert, new java.security.SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			} catch(Exception e) {
				Log.e(InformaConstants.TAG, "cannot init ssl context: " + e.getMessage());
			}
		}
		
		public HttpResponse executeGet() throws ClientProtocolException, IOException {
			HttpGet httpget = new HttpGet(url);
    		return httpclient.execute(httpget);
		}
		
		public HttpResponse executePost(List<NameValuePair> nameValuePair) throws ClientProtocolException, IOException {
			HttpPost httppost = new HttpPost(url);
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePair));
			return httpclient.execute(httppost);
		}
	}
	
}
