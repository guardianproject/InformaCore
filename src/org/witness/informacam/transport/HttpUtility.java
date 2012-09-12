package org.witness.informacam.transport;

import info.guardianproject.iocipher.File;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
import javax.net.ssl.X509KeyManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Transport;

import android.content.Context;
import android.util.Log;

public class HttpUtility {
	public static String executeHttpsPost(final Context c, final String host, final Map<String, Object> postData, final long pkc12Id, final File file) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<String> future = ex.submit(new Callable<String>() {
			String result = Transport.Result.FAIL;
			String hostname;
			
			URL url;
			HttpsURLConnection connection;
			HostnameVerifier hnv;
			Proxy proxy;
			DataOutputStream dos;
			SSLContext ssl;
			
			InformaTrustManager itm;
			
			private void buildQuery() {
				Iterator<Entry<String, Object>> it = postData.entrySet().iterator();
				StringBuffer sb = new StringBuffer();
				
				connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + Transport.Keys.BOUNDARY);
				
				try {
					dos = new DataOutputStream(connection.getOutputStream());
										
					while(it.hasNext()) {
						Entry<String, Object> e = it.next();
						
						sb.append(Transport.Keys.HYPHENS + Transport.Keys.BOUNDARY + Transport.Keys.LINE_END);
						
						sb.append("Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + Transport.Keys.LINE_END);
						sb.append("Content-Type: text/plain" + Transport.Keys.LINE_END + Transport.Keys.LINE_END);
						sb.append(String.valueOf(e.getValue()) + Transport.Keys.LINE_END);
						Log.d(Transport.LOG, sb.toString());
						dos.writeBytes(sb.toString());
					}
					
					// TODO: not this; the J3M is what we want to send
					if(file != null) {
						dos.writeBytes("Content-Disposition: form-data; name=\"InformaCamUpload\";filename=\"" + file.getName() + "\"" + Transport.Keys.LINE_END);
						dos.writeBytes("Content-Type: text/plain;");
						
						
						byte[] fileBytes = IOUtility.getBytesFromFile(file);
						int index = 0;
						int bufSize = 1024;
						
						do {
							if((index + bufSize) > fileBytes.length)
								bufSize = fileBytes.length - index;
							dos.write(fileBytes, index, bufSize);
							index += bufSize;
						} while(index < fileBytes.length);
						
						dos.writeBytes(Transport.Keys.LINE_END);
					}
					
					dos.writeBytes(Transport.Keys.HYPHENS + Transport.Keys.BOUNDARY + Transport.Keys.HYPHENS + Transport.Keys.LINE_END);
					
					dos.flush();
					dos.close();
				} catch (IOException e) {
					Log.e(Transport.LOG, e.toString());
					e.printStackTrace();
				}
			}
			
			@Override
			public String call() throws Exception {
				hostname = host.split("/")[0];
				url = new URL("https://" + host);
				
				Log.d(Transport.LOG, hostname);
				Log.d(Transport.LOG, url.toString());
				
				hnv = new HostnameVerifier() {
					@Override
					public boolean verify(String hn, SSLSession session) {
						if(hn.equals(hostname))
							return true;
						else
							return false;
					}
				};
				
				itm = new InformaTrustManager(c);
				
				// TODO: perform logic to tell ITM which key it should use
				
				ssl = SSLContext.getInstance("TLS");
				X509KeyManager[] x509KeyManager = null;
				
				if(pkc12Id != 0L)
					x509KeyManager = itm.getKeyManagers(pkc12Id);
				
				ssl.init(x509KeyManager, new TrustManager[] {itm}, new SecureRandom());
				
				HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
				HttpsURLConnection.setDefaultHostnameVerifier(hnv);
				
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8118));
				
				connection = (HttpsURLConnection) url.openConnection(proxy);
				
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Connection", "Keep-Alive");
				connection.setUseCaches(false);
				connection.setDoInput(true);
				connection.setDoOutput(true);
				
				buildQuery();
				
				try {
					InputStream is = connection.getInputStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					String line;
					StringBuffer sb = new StringBuffer();
					while((line = br.readLine()) != null)
						sb.append(line);
					br.close();
					connection.disconnect();
					result = sb.toString();
				} catch(NullPointerException e) {
					Log.e(Transport.LOG, e.toString());
					e.printStackTrace();
				}
				return result;
			}
			
		});
		
		try {
			return future.get();
		} catch (InterruptedException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			Log.e(Transport.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}
	
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
