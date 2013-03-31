package org.witness.informacam.transport;

import info.guardianproject.onionkit.proxy.SocksProxyClientConnOperator;
import info.guardianproject.onionkit.trust.StrongHttpsClient;
import info.guardianproject.onionkit.trust.StrongSSLSocketFactory;
import info.guardianproject.onionkit.trust.StrongTrustManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.LayeredSchemeSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IConnection;
import org.witness.informacam.models.IKeyStore;
import org.witness.informacam.models.IResult;
import org.witness.informacam.models.ITransportCredentials;
import org.witness.informacam.utils.Constants.App.Crypto;
import org.witness.informacam.utils.Constants.App.Transport;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.IManifest;
import org.witness.informacam.utils.Constants.Models;

import android.content.Context;
import android.util.Log;

public class HttpUtility {
	private final static String LOG = Transport.LOG;
	private final static String PROXY_HOST = "127.0.0.1";
	private final static int PROXY_PORT = 8118;

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
		}

		Log.d(LOG, "REQUEST RESULT:\n" + sb.toString());
		return result;
	}

	public static IConnection executeHttpGet(final IConnection connection) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<IConnection> future = ex.submit(new Callable<IConnection>() {
			@Override
			public IConnection call() throws Exception {
				InformaCam informaCam = InformaCam.getInstance();
				HttpClient http = new DefaultHttpClient();

				http.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(PROXY_HOST, PROXY_PORT));
				HttpGet get = new HttpGet(connection.url);
				get = connection.build(get);

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

	public static IConnection executeHttpPost(final IConnection connection) {
		ExecutorService ex = Executors.newFixedThreadPool(100);
		Future<IConnection> future = ex.submit(new Callable<IConnection>() {
			@Override
			public IConnection call() throws Exception {
				InformaCam informaCam = InformaCam.getInstance();
				HttpClient http = new IHttpClient(connection);

				http.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(PROXY_HOST, PROXY_PORT));
				HttpPost post = new HttpPost(connection.url);
				post = connection.build(post);

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

	public static class IHttpClient extends DefaultHttpClient {
		private SchemeRegistry mRegistry;
		private ISSLSocketFactory sFactory;
		
		public IHttpClient(IConnection connection) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
			sFactory = new ISSLSocketFactory(connection);
			mRegistry.register(new Scheme("https", sFactory, connection.port));
		}

	}

	public static class ISSLSocketFactory extends org.apache.http.conn.ssl.SSLSocketFactory implements LayeredSchemeSocketFactory {
		ITrustManager itm;
		private SSLSocketFactory mFactory = null;

		private Proxy mProxy = null;

		public static final String TLS   = "TLS";
		public static final String SSL   = "SSL";
		public static final String SSLV2 = "SSLv2";

		public ISSLSocketFactory (IConnection connection) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
		{
			super(null);

			SSLContext sslContext = SSLContext.getInstance ("TLS");
			itm = new ITrustManager();
			TrustManager[] tm = new TrustManager[] { itm };
			KeyManager[] km = itm.getKeyManagers(connection.transportCredentials);
			sslContext.init (km, tm, new SecureRandom ());

			mFactory = sslContext.getSocketFactory ();

		}

		@Override
		public Socket createSocket() throws IOException
		{
		    return mFactory.createSocket();
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port,
				boolean autoClose) throws IOException, UnknownHostException {
		
			return mFactory.createSocket(socket, host, port, autoClose);
		}

		

		@Override
		public boolean isSecure(Socket sock) throws IllegalArgumentException {
			return (sock instanceof SSLSocket);
		}
		

		public void setProxy (Proxy proxy) {
			mProxy = proxy;
		}
		
		public Proxy getProxy ()
		{
			return mProxy;
		}
		
		class StrongHostNameResolver implements HostNameResolver
		{

			@Override
			public InetAddress resolve(String host) throws IOException {
				
				//can we do proxied name look ups here?
				
				//what can we implement to make name resolution strong
				
				return InetAddress.getByName(host);
			}
			
		}

		@Override
		public Socket connectSocket(Socket sock, InetSocketAddress arg1,
				InetSocketAddress arg2, HttpParams arg3) throws IOException,
				UnknownHostException, ConnectTimeoutException {
		
			return connectSocket(sock, arg1.getHostName(), arg1.getPort(), InetAddress.getByName(arg2.getHostName()), arg2.getPort(),arg3);
		}

		@Override
		public Socket createSocket(HttpParams arg0) throws IOException {
			
			return createSocket();
			
		}

		@Override
		public Socket createLayeredSocket(Socket arg0, String arg1, int arg2,
				boolean arg3) throws IOException, UnknownHostException {
			return ((LayeredSchemeSocketFactory)mFactory).createLayeredSocket(arg0, arg1, arg2, arg3);
		}

	}
	
	public static class ITrustManager implements X509TrustManager {
		private KeyStore keyStore;
		private X509TrustManager defaultTrustManager;
		private X509TrustManager appTrustManager;

		private InformaCam informaCam;
		private IKeyStore keyStoreManifest;

		private final static String LOG = Crypto.LOG;

		public ITrustManager() {
			informaCam = InformaCam.getInstance();

			loadKeyStore();

			defaultTrustManager = getTrustManager(false);
			appTrustManager = getTrustManager(true);
		}

		private void loadKeyStore() {
			try {
				keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			} catch(KeyStoreException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			
			try {
				keyStoreManifest = (IKeyStore) informaCam.getModel(new IKeyStore());
				if(keyStoreManifest.path == null) {
					keyStore.load(null, null);
				} else {
					keyStore.load(new ByteArrayInputStream(informaCam.ioService.getBytes(keyStoreManifest.path, Type.IOCIPHER)), keyStoreManifest.password.toCharArray());
				}
			} catch(CertificateException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}

		private boolean updateKeyStore(byte[] newKey) {
			if(informaCam.ioService.saveBlob(newKey, new info.guardianproject.iocipher.File(IManifest.KEY_STORE))) {
				keyStoreManifest.lastModified = System.currentTimeMillis();
				informaCam.saveState(keyStoreManifest);
				
				return true;
			}
			
			return false;
		}

		private void storeCertificate(X509Certificate[] chain) {
			try {
				for(X509Certificate cert : chain) {
					keyStore.setCertificateEntry(cert.getSubjectDN().toString(), cert);
				}
			} catch(KeyStoreException e) {
				Log.e(Crypto.LOG, "keystore exception: " + e.toString());
			}

			appTrustManager = getTrustManager(true);
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				keyStore.store(baos, keyStoreManifest.password.toCharArray());
				updateKeyStore(baos.toByteArray());
				Log.d(Crypto.LOG, "new key encountered!  length: " + baos.size());
			} catch(KeyStoreException e) {
				Log.e(Crypto.LOG, "keystore exception: " + e.toString());	
			} catch (NoSuchAlgorithmException e) {
				Log.e(Crypto.LOG, "no such algo exception: " + e.toString());
			} catch (IOException e) {
				Log.e(Crypto.LOG, "IOException: " + e.toString());
			} catch (CertificateException e) {
				Log.e(Crypto.LOG, "Certificate Exception: " + e.toString());
			}
		}
		
		public X509KeyManager[] getKeyManagers(ITransportCredentials transportCredentials) {
			if(transportCredentials == null) {
				return null;
			}
			
			KeyManagerFactory kmf = null;
			KeyManager[] km = null;
			X509KeyManager[] xkm = null;
			try {
				kmf = KeyManagerFactory.getInstance("X509");
				KeyStore xks = KeyStore.getInstance("PKCS12");
				
				byte[] keyBytes = informaCam.ioService.getBytes(transportCredentials.certificatePath, Type.IOCIPHER);
				ByteArrayInputStream bais = new ByteArrayInputStream(keyBytes);
				xks.load(bais, transportCredentials.certificatePassword.toCharArray());
				
				kmf.init(xks, keyStoreManifest.password.toCharArray());
				km = kmf.getKeyManagers();
				xkm = new X509KeyManager[km.length];

				for(int x=0;x<km.length;x++) {
					X509KeyManager k = (X509KeyManager) km[x];
					xkm[x] = k;
				}
				
				return xkm;
			} catch (NoSuchAlgorithmException e) {
				Log.e(Crypto.LOG, e.toString());
				e.printStackTrace();
			} catch (KeyStoreException e) {
				Log.e(Crypto.LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(Crypto.LOG, e.toString());
				e.printStackTrace();
			} catch (CertificateException e) {
				Log.e(Crypto.LOG, e.toString());
				e.printStackTrace();
			} catch (UnrecoverableKeyException e) {
				Log.e(Crypto.LOG, e.toString());
				e.printStackTrace();
			}

			return null;
			
		}
		
		private X509TrustManager getTrustManager(boolean withKeystore) {
			try {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
				if(withKeystore) {
					tmf.init(keyStore);
				} else {
					tmf.init((KeyStore) null);
				}

				for(TrustManager t : tmf.getTrustManagers()) {
					if(t instanceof X509TrustManager) {
						return (X509TrustManager) t;
					}
				}
			} catch (KeyStoreException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
			return null;
		}

		private boolean isCertKnown(X509Certificate cert) {
			try {
				return keyStore.getCertificateAlias(cert) != null;
			} catch(KeyStoreException e) {
				return false;
			}
		}

		private boolean isExpiredException(Throwable e) {
			do {
				if(e instanceof CertificateExpiredException)
					return true;
				e = e.getCause();
			} while(e != null);
			return false;
		}
		
		private void checkCertificateTrusted(X509Certificate[] chain, String authType, boolean isServer) throws CertificateException {
			try {
				if(isServer)
					appTrustManager.checkServerTrusted(chain, authType);
				else
					appTrustManager.checkClientTrusted(chain, authType);
			} catch(CertificateException e) {
				if(isExpiredException(e))
					return;
				if(isCertKnown(chain[0]))
					return;

				try {
					if(isServer)
						defaultTrustManager.checkServerTrusted(chain, authType);
					else
						defaultTrustManager.checkClientTrusted(chain, authType);
				} catch(CertificateException ce) {
					storeCertificate(chain);
				}
			}
		}
		
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			checkCertificateTrusted(chain, authType, false);
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			checkCertificateTrusted(chain, authType, true);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return defaultTrustManager.getAcceptedIssuers();
		}

	}
}
