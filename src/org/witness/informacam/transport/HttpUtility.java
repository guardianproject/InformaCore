package org.witness.informacam.transport;

import info.guardianproject.onionkit.trust.StrongHttpsClient;
import info.guardianproject.onionkit.trust.StrongSSLSocketFactory;
import info.guardianproject.onionkit.trust.StrongTrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.models.IResult;
import org.witness.informacam.models.ITransportCredentials;
import org.witness.informacam.models.connections.IConnection;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.Models;

import android.content.Context;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import de.duenndns.ssl.MemorizingTrustManager;

public class HttpUtility {
	private final static String LOG = Transport.LOG;
	
	//these need to be moved to app preferences or at least app global constants; may want to support other proxy systems
	private final static String PROXY_TYPE = "SOCKS";
	private final static String PROXY_HOST = "127.0.0.1";
	private final static int PROXY_PORT = 9050;

	//single shared static instance so we don't have to reload keystores etc
	private static DefaultHttpClient mHttpClient = null;
	

	private static IResult parseResponse(HttpResponse response) {
		IResult result = new IResult();

		StringBuffer sb = new StringBuffer();
		StringBuffer resultBuffer = new StringBuffer();

		sb.append(response.getStatusLine()).append("\n\n");
		if(String.valueOf(response.getStatusLine()).contains(Transport.Results.OK)) {
			result.code = Integer.parseInt(Transport.Results.OK);
		} else {
			result.code = Integer.parseInt(Transport.Results.FAIL[1]);
		}

		try {
			InputStream is = response.getEntity().getContent();
			for(String line : org.apache.commons.io.IOUtils.readLines(is)) {
				sb.append(line);
				resultBuffer.append(line);
			}

			JSONObject resultObject = (JSONObject) new JSONTokener(resultBuffer.toString()).nextValue();
			if(result.code == Integer.parseInt(Transport.Results.OK)) {
				result.data = resultObject.getJSONObject(Models.IResult.DATA);
				result.responseCode = resultObject.getInt(Models.IResult.RESPONSE_CODE);
			} else {
				result.reason = resultObject.getString(Models.IResult.REASON);
			}

		} catch (IllegalStateException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (ClassCastException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		Log.d(LOG, "REQUEST RESULT:\n" + sb.toString());
		return result;
	}

	public IConnection executeHttpGet(final IConnection connection, final Context context) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<IConnection> future = ex.submit(new Callable<IConnection>() {
			@Override
			public IConnection call() throws Exception {
				
				DefaultHttpClient http = getHttpClientInstance(context, connection);
				
				HttpGet get = new HttpGet(connection.build());
				
				Log.d(LOG, "executing get");

				connection.numTries++;
				connection.result = parseResponse(http.execute(get));
				return connection;
			}

		});
		try {
			return future.get();
		} catch (InterruptedException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}

	public IConnection executeHttpPost(final IConnection connection, final Context context) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<IConnection> future = ex.submit(new Callable<IConnection>() {
			@Override
			public IConnection call() throws Exception {

				DefaultHttpClient http = getHttpClientInstance (context, connection);
				
				HttpPost post = new HttpPost(connection.url);
				post = connection.build(post);
				Log.d(LOG, "executing post");

				connection.numTries++;
				connection.result = parseResponse(http.execute(post));
				return connection;
			}

		});
		try {
			return future.get();
		} catch (InterruptedException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}
	
	private static synchronized DefaultHttpClient getHttpClientInstance (Context context, IConnection connection) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException
	{

		if (mHttpClient == null)
		{
			
			//init store for client certificates
			ITransportCredentials iCreds = connection.transportCredentials;
			ClientTrustManager cTrustManager = new ClientTrustManager ();
			
			//put memorizing trust manager here
			MemorizingTrustManager memTrustManager = new MemorizingTrustManager (context, cTrustManager, null);
			
			TrustManager[] tManagers = new TrustManager[]{ memTrustManager};

			X509KeyManager[] km = null;

			if(connection.transportCredentials != null) {
				km = cTrustManager.getKeyManagers(connection.transportCredentials);
			}
			
			 SSLContext sslContext = SSLContext.getInstance ("TLS");
		     sslContext.init (km, tManagers, new SecureRandom ());
		     
		     KeyStore keyStore = cTrustManager.getKeyStore();

			StrongSSLSocketFactory sFactory = new StrongSSLSocketFactory(context,keyStore,sslContext.getSocketFactory ());
			
			mHttpClient = new StrongHttpsClient(context,cTrustManager, sFactory);
			((StrongHttpsClient)mHttpClient).useProxy(true, PROXY_TYPE, PROXY_HOST, PROXY_PORT);
		}
		
		return mHttpClient;
	}
	/*
	public class ISocketFactory extends org.apache.http.conn.ssl.SSLSocketFactory {
		SSLContext sslContext;
		
		public ISocketFactory(KeyStore keyStore, IConnection connection) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
			super(null);
		
			sslContext = SSLContext.getInstance ("TLS");
			ITrustManager itm = new ITrustManager();
			X509KeyManager[] km = null;

			if(connection.transportCredentials != null) {
				km = itm.getKeyManagers(connection.transportCredentials);
			}

			sslContext.init (km, new TrustManager[] { itm }, new SecureRandom ());
		}
		
		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
			return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
		}
		
		@Override
		public Socket createSocket() throws IOException {
			return sslContext.getSocketFactory().createSocket();
		}
	}

	*/
}
