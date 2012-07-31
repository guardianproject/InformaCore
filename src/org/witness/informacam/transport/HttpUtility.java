package org.witness.informacam.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.witness.informacam.utils.Constants.Crypto;

import android.content.Context;
import android.util.Log;

public class HttpUtility {
	
	public static String executeHttpGet(String url) throws ClientProtocolException, URISyntaxException, IOException, InterruptedException, ExecutionException {
		return HttpUtility.executeHttpGet(url, false);
	}
	
	public static String executeHttpGet(final String url, final boolean proxy) throws URISyntaxException, ClientProtocolException, IOException, InterruptedException, ExecutionException {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<String> future = ex.submit(new Callable<String>() {

			@Override
			public String call() throws Exception {
				HttpClient client = new DefaultHttpClient();
				
				if(proxy) {
					HttpHost host = new HttpHost("localhost", 8118);
					client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, host);
				}
				
				HttpGet request = new HttpGet();
				request.setURI(new URI(url));
				HttpResponse res = client.execute(request);
				
				BufferedReader br = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
				StringBuffer sb = new StringBuffer();
				String line = "";
				while((line = br.readLine()) != null)
					sb.append(line + "\r\n");
				br.close();
						
				return sb.toString();
			}
			
		});
		
		String res = future.get();
		ex.shutdown();
		
		return res;
	}
	
	public static HttpsURLConnection initHttpsConnection(Context c, String urlString, boolean useProxy) {
		HttpsURLConnection connection;
		InformaTrustManager itm = new InformaTrustManager(c);
		
		try {
			URL url = new URL(urlString);
			final String hostString = urlString.split("https://")[1].split("/")[0];
			HostnameVerifier hnv = new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					if(hostname.equals(hostString)) {
						return true;
					} else
						return false;
				}
				
			};
			
			SSLContext ssl = SSLContext.getInstance("TLS");
			ssl.init(null, new TrustManager[] { itm }, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hnv);
			
			if(useProxy || hostString.contains(".onion")) {
				Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8118));
				connection = (HttpsURLConnection) url.openConnection(proxy);
			} else
				connection = (HttpsURLConnection) url.openConnection();
			
		} catch (NoSuchAlgorithmException e) {
			Log.e(Crypto.LOG, e.toString());
			return null;
		} catch (KeyManagementException e) {
			Log.e(Crypto.LOG, e.toString());
			return null;
		} catch (IOException e) {
			Log.e(Crypto.LOG, e.toString());
			return null;
		}
		
		return connection;
	}
	
	public static String executeHttpsGet(Context c, String urlString) throws ClientProtocolException, URISyntaxException, IOException {
		return executeHttpsGet(c, urlString, false);
	}
	
	public static String executeHttpsGet(Context c, String urlString, boolean useProxy) throws ClientProtocolException, URISyntaxException, IOException {
		HttpsURLConnection connection = HttpUtility.initHttpsConnection(c, urlString, useProxy);
		if(connection == null)
			return null;
		
		connection.setRequestMethod("GET");
		InputStream is = connection.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuffer sb = new StringBuffer();
		while((line = br.readLine()) != null)
			sb.append(line);
		
		br.close();
		connection.disconnect();
		
		return sb.toString();
	}
	
	// TODO: public static String executeHttpsPost(Context c, String urlString, boolean useProxy)
}
