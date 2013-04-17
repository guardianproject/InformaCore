package org.witness.informacam.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.IKeyStore;
import org.witness.informacam.models.ITransportCredentials;
import org.witness.informacam.utils.Constants.App.Crypto;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.IManifest;

import android.util.Log;

public class ClientTrustManager implements X509TrustManager {
	private KeyStore keyStore;
	private X509TrustManager defaultTrustManager;
	private X509TrustManager appTrustManager;

	private InformaCam informaCam;
	private IKeyStore keyStoreManifest;
	
	private boolean hasKeyStore;

	private final static String LOG = Crypto.LOG;

	public ClientTrustManager() {
		informaCam = InformaCam.getInstance();

		hasKeyStore = loadKeyStore();

		defaultTrustManager = getTrustManager(false);
		appTrustManager = getTrustManager(true);
	}

	public KeyStore getKeyStore ()
	{
		return keyStore;
	}
	
	private boolean loadKeyStore() {
		try {
			keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		} catch(KeyStoreException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		try {
			keyStoreManifest = new IKeyStore();
			keyStoreManifest.inflate(informaCam.ioService.getBytes(IManifest.KEY_STORE_MANIFEST, Type.IOCIPHER));

		} catch(Exception e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		

		try {
			keyStore.load(null, null);
			
			ByteArrayInputStream bais = null;
			try {
				bais = new ByteArrayInputStream(informaCam.ioService.getBytes(keyStoreManifest.path, Type.IOCIPHER));
			} catch(NullPointerException e) {}
			
			if(bais != null && bais.available() > 0) {
				keyStore.load(bais, keyStoreManifest.password.toCharArray());
				return true;
			} else {
				Log.d(LOG, "KEY STORE INITED");
				return false;
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
		
		return false;
	}

	private boolean updateKeyStore(byte[] newKeyStore) {
		if(informaCam.ioService.saveBlob(newKeyStore, new info.guardianproject.iocipher.File(IManifest.KEY_STORE))) {
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
				Log.d(LOG, "setting certificate entry: " + cert.getSubjectDN().toString());
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
		} catch (NullPointerException e) {
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