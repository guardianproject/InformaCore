package org.witness.informa.utils.secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;

import android.util.Log;

public class DestoService {
	String destoQuery;
	
	public DestoService(ArrayList<String> destos) {
		StringBuffer sb = new StringBuffer();
		for(String desto : destos)
			sb.append("," + desto);
		destoQuery = sb.toString().substring(1);
	}
		
	public ArrayList<Map<String, String>> getDestos() throws JSONException {
		ArrayList<Map<String, String>> destos = new ArrayList<Map<String, String>>();
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<String> f = ex.submit(new Callable<String>() {
			private final HostnameVerifier NO_VER = new HostnameVerifier() {
				public boolean verify(String host, SSLSession session) {
					/*
					if(host.regionMatches(0, InformaConstants.REPO, 0, InformaConstants.REPO.length() - 1)) {
						return true;
					} else {
						return false;
					}
					*/
					return true;
				}
			};
			
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
			
			private String queryRepo() {
				try {
					URL url = new URL("https://" + InformaConstants.REPO + destoQuery);
					trustThisHost();
					HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
					https.setHostnameVerifier(NO_VER);
					
					if(https.getResponseCode() == HttpURLConnection.HTTP_OK) {
						InputStream is = https.getInputStream();
						BufferedReader br = new BufferedReader(new InputStreamReader(is));
						
						StringBuffer sb = new StringBuffer();
						String r;
						while((r = br.readLine()) != null)
							sb.append(r);
						
						br.close();
						is.close();
						https.disconnect();
						return sb.toString();
					} else {
						return null;
					}
				} catch(Exception e) {
					Log.e(InformaConstants.TAG, "https error in queryRepo(): " + e.toString());
					return null;
				}
			}
			
			public String call() {
				return queryRepo();
			}
		});
		
		try {
			JSONArray jsond = (JSONArray) ((JSONObject) new JSONTokener(f.get()).nextValue()).get(TrustedDestinations.HOOKUPS);
			for(int i=0; i<jsond.length(); i++) {
				Map<String, String> desto = new HashMap<String, String>();
				JSONObject destoj = (JSONObject) jsond.get(i);
				desto.put(destoj.getString(TrustedDestinations.EMAIL), destoj.getString(TrustedDestinations.DESTO));
				destos.add(desto);
			}
			return destos;
		} catch(InterruptedException e) {
			Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			return null;
		} catch(ExecutionException e) {
			Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			return null;
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			return null;
		}
		
		
	}
}
