package org.witness.informa.utils.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Media;
import org.witness.informa.utils.InformaConstants.Keys.Settings;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.InformaConstants.MediaTypes;
import org.witness.informa.utils.MetadataPack;
import org.witness.ssc.MediaManager;
import org.witness.ssc.R;
import org.witness.ssc.utils.ObscuraConstants;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class Uploader extends Service {
	private final IBinder binder = new LocalBinder();
	private List<MetadataPack> queue;
	boolean isCurrentlyUploading = false;
	public static Uploader uploader;
	Intent uploadStatus;
	NotificationManager nm;
	DatabaseHelper dh;
	SQLiteDatabase db; 
	SharedPreferences sp;
	
	SSLContext ssl;
	InformaTrustManager itm;
	
	Handler h;
	
	public final String TAG = InformaConstants.TAG.replace("INFORMA", "INFORMA UPLOADER SERVICE");
	
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
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	@Override
	public void onCreate() {
		Log.d(InformaConstants.TAG, "UPLOADER SERVICE STARTED!");
		queue = new ArrayList<MetadataPack>();
		uploader = this;
		sendBroadcast(new Intent().setAction(Keys.Service.UPLOADER_AVAILABLE));
		uploadStatus = new Intent(this, MediaManager.class);
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Settings.HAS_DB_PASSWORD, ""));
		
		//pickupUploads();
	}
	
	public static Uploader getUploader() {
		return uploader;
	}
	
	private void pickupUploads() {
		// TODO: pick up where uploader left off.
		dh.setTable(db, Tables.IMAGES);
		Cursor c = dh.getValue(db, new String[] {
				BaseColumns._ID,
				Keys.Image.UNREDACTED_IMAGE_HASH,
				Keys.Image.LOCATION_OF_OBSCURED_VERSION,
				TrustedDestinations.DESTO,
				Media.MEDIA_TYPE,
				Media.SHARE_VECTOR,
				Media.STATUS,
				Keys.Uploader.AUTH_TOKEN,
				Media.KEY_HASH
		}, Media.STATUS, InformaConstants.Media.Status.UPLOADING);
		if(c != null && c.getCount() > 0) {
			c.moveToFirst();
			while(!c.isAfterLast()) {
				if(
					c.getInt(c.getColumnIndex(Media.SHARE_VECTOR)) == InformaConstants.Media.ShareVector.ENCRYPTED_UPLOAD_QUEUE ||
					c.getInt(c.getColumnIndex(Media.SHARE_VECTOR)) == InformaConstants.Media.ShareVector.UNENCRYPTED_UPLOAD_QUEUE
				) {
					try {
						MetadataPack mp = new MetadataPack(
								null,
								c.getInt(c.getColumnIndex(BaseColumns._ID)),
								null,
								null,
								c.getString(c.getColumnIndex(Keys.Image.LOCATION_OF_OBSCURED_VERSION)),
								c.getString(c.getColumnIndex(Keys.Image.UNREDACTED_IMAGE_HASH)),
								c.getInt(c.getColumnIndex(Media.MEDIA_TYPE)),
								c.getString(c.getColumnIndex(Media.KEY_HASH)));
						mp.setTDDestination(c.getString(c.getColumnIndex(TrustedDestinations.DESTO)));
						mp.authToken = c.getString(c.getColumnIndex(Keys.Uploader.AUTH_TOKEN));
						mp.id = c.getInt(c.getColumnIndex(BaseColumns._ID));
						
						addToQueue(mp);
					} catch(NullPointerException e){}
					catch (KeyManagementException e) {
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				}
			}
			c.close();
		}
		
	}
	
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		updateQueue();
		db.close();
		dh.close();
		Log.d(InformaConstants.TAG, "UPLOADER SERVICE ENDED.");
	}
	
	private void updateQueue() {
		dh.setTable(db, Tables.IMAGES);
		for(MetadataPack mp : queue) {
			ContentValues cv = new ContentValues();
			if(mp.authToken != null)
				cv.put(Keys.Uploader.AUTH_TOKEN, mp.authToken);
			if(mp.tdDestination != null)
				cv.put(Keys.TrustedDestinations.DESTO, mp.tdDestination);
			if(mp.messageUrl != null)
				cv.put(Keys.Media.Manager.MESSAGE_URL, mp.messageUrl);
			cv.put(Media.STATUS, mp.status);
			db.update(dh.getTable(), cv, BaseColumns._ID + " =?", new String[] {Long.toString(mp.id)});
		}
	}
	
	private int parseResult(JSONObject res) throws JSONException {
		try {
			Log.d(TAG, res.toString());
			if(res.getString("result").equals(Keys.Uploader.A_OK))
				return InformaConstants.Uploader.RequestCodes.A_OK;
			else {
				if(res.getString("result").equals(Keys.Uploader.POSTPONE))
					return InformaConstants.Uploader.RequestCodes.POSTPONE;
				else
					return InformaConstants.Uploader.RequestCodes.RETRY;
			}
		} catch(NullPointerException e) {
			Log.e(TAG, "Server returned null due to timeout or malformed request. tyring again");
			return InformaConstants.Uploader.RequestCodes.RETRY;
		}
			
			
	}
	
	private void updateQueueStatus() {
		for(MetadataPack mp : queue) {
			if(mp.status == InformaConstants.Media.Status.UPLOADING)
				return;
		}
		
		Log.d(TAG, "stopping uploader activity.");
		stopSelf();
	}
	
	private JSONObject scheduleUpload(MetadataPack mp) throws ClientProtocolException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, JSONException {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put(Keys.Uploader.Entities.USER_PGP, mp.keyHash);
		nvp.put(Keys.Uploader.Entities.AUTH_TOKEN, mp.authToken);
		nvp.put(Keys.Uploader.Entities.BYTES_EXPECTED, new File(mp.filepath).length());
		
		Log.d(TAG, nvp.toString());
		
		InformaConnectionFactory connection = new InformaConnectionFactory(mp.tdDestination);
		try {
			return connection.executePost(nvp);
		} catch(NullPointerException e) {
			Log.d(TAG, "NPE HERE! " + e.toString());
			e.printStackTrace();
			return (JSONObject) new JSONTokener(InformaConstants.Uploader.Results.POSTPONE).nextValue();
		}
	}
	
	private JSONObject getUploadTicket(MetadataPack mp) throws ClientProtocolException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, JSONException  {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put(Keys.Uploader.Entities.USER_PGP, mp.keyHash);
		nvp.put(Keys.Uploader.Entities.TIMESTAMP_CREATED, mp.timestampCreated);
		nvp.put(Keys.Uploader.Entities.MEDIA_TYPE, mp.mediaType);
		
		Log.d(TAG, nvp.toString());
		
		InformaConnectionFactory connection = new InformaConnectionFactory(mp.tdDestination);
		
		try {
			return connection.executePost(nvp);
		} catch(NullPointerException e) {
			return (JSONObject) new JSONTokener(InformaConstants.Uploader.Results.POSTPONE).nextValue();
		}
		
	}
	
	private JSONObject uploadMedia(MetadataPack mp) throws ClientProtocolException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, JSONException {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put(Keys.Uploader.Entities.USER_PGP, mp.keyHash);
		nvp.put(Keys.Uploader.Entities.AUTH_TOKEN, mp.authToken);
		
		InformaConnectionFactory connection = new InformaConnectionFactory(mp.tdDestination);
		try {
			return connection.executePost(nvp, new File(mp.filepath), mp.mediaType);
		} catch(NullPointerException e) {
			return (JSONObject) new JSONTokener(InformaConstants.Uploader.Results.POSTPONE).nextValue();
		}
	}
	
	public JSONObject getMessages(String desto, String messageUrl, String keyHash) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, JSONException {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put(Keys.Uploader.Entities.USER_PGP, keyHash);
		nvp.put(Keys.Media.Manager.MESSAGE_URL, messageUrl);
		
		InformaConnectionFactory connection = new InformaConnectionFactory(desto);
		try {
			return connection.executePost(nvp);
		} catch(NullPointerException e) {
			return (JSONObject) new JSONTokener(InformaConstants.Uploader.Results.POSTPONE).nextValue();
		}
	}
	
	public void testPing() throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, ClientProtocolException, KeyStoreException, IOException {
		Log.d(InformaConstants.TAG, "STARTING UPLOADING!");
		InformaConnectionFactory icf = new InformaConnectionFactory("rgr4us5kmgxombaf.onion");
		
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put("user_pgp", "blahblahbblah");
		nvp.put("test", "OH SHIT");
		Log.d(TAG, "OMG:\n" + icf.executePost(nvp));
	}
	
	private void uploadImage(MetadataPack mp) {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put("user_pgp", mp.keyHash);
		nvp.put("auth_token", mp.authToken);
		nvp.put("image_upload", new File(mp.filepath));
	}
	
	private void uploadVideo(MetadataPack mp) {
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put("user_pgp", mp.keyHash);
		nvp.put("auth_token", mp.authToken);
		nvp.put("video_upload", new File(mp.filepath));
	}
	
	private void startUploading() throws NoSuchAlgorithmException, KeyManagementException {
		for(final MetadataPack mp : queue) {
			if(mp.tdDestination != null) {
				// in a thread...
				mp.status = InformaConstants.Media.Status.UPLOADING;
				new Thread(new Runnable() {
					private static final int THREADS = 10;
					
					@Override
					public void run() {
						if(mp.status == InformaConstants.Media.Status.UPLOAD_FAILED) {
							updateQueueStatus();
							return;
						} else if(mp.retryFlags > 7) {
							mp.status = InformaConstants.Media.Status.UPLOAD_FAILED;
							updateQueueStatus();
							return;
						}
						
						Log.d(TAG, "HEY WE ARE STARTING THIS PROCESS!");
						JSONObject res;
						try {
							ExecutorService ex = Executors.newFixedThreadPool(THREADS);
							if(mp.authToken == null) {
								Future<JSONObject> getUploadTicket = ex.submit(new Callable<JSONObject>() {

									@Override
									public JSONObject call() throws Exception {
										return getUploadTicket(mp);
									}
									
								});
								res = getUploadTicket.get();
								if(parseResult(res) == InformaConstants.Uploader.RequestCodes.A_OK) {
									mp.authToken = res.getString(Keys.Uploader.AUTH_TOKEN);
								} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.RETRY) {
									mp.retryFlags++;
									run();
								} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.POSTPONE) {
									return;
								}
								
							}
							
							Future<JSONObject> scheduleUpload = ex.submit(new Callable<JSONObject>() {
								@Override
								public JSONObject call() throws Exception {
									return scheduleUpload(mp);
								}
							});
							res = scheduleUpload.get();
							if(parseResult(res) == InformaConstants.Uploader.RequestCodes.A_OK) {
								// HANDLE RESULT?
							} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.RETRY) {
								mp.retryFlags++;
								run();
							} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.POSTPONE) {
								return;
							}
							
							Future<JSONObject> uploadMedia = ex.submit(new Callable<JSONObject>() {
								@Override
								public JSONObject call() throws Exception {
									return uploadMedia(mp);
								}
							});
							res = uploadMedia.get();
							if(parseResult(res) == InformaConstants.Uploader.RequestCodes.A_OK) {
								mp.status = InformaConstants.Media.Status.UPLOAD_COMPLETE;
								mp.messageUrl = res.getString(Keys.Media.Manager.MESSAGE_URL);
								updateQueueStatus();								
							} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.RETRY) {
								mp.retryFlags++;
								run();
							} else if(parseResult(res) == InformaConstants.Uploader.RequestCodes.POSTPONE) {
								return;
							}
							
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (ExecutionException e) {
							e.printStackTrace();
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		}
	}
	
	public void addToQueue(MetadataPack mp) throws KeyManagementException, NoSuchAlgorithmException {
		queue.add(mp);
		if(!isCurrentlyUploading)
			startUploading();
	}
	
	public void addToQueue(ArrayList<MetadataPack> mp) throws KeyManagementException, NoSuchAlgorithmException {
		queue.addAll(mp);
		if(!isCurrentlyUploading)
			startUploading();
	}
	
	public String getDestos(String query) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException {
		Log.d(TAG, "Getting destos...");
		InformaConnectionFactory icf = new InformaConnectionFactory("j3m.info/repo/");
		Map<String, Object> nvp = new HashMap<String, Object>();
		nvp.put("repo", query);
		
		return icf.executePost(nvp).toString();
	}
	
	@SuppressWarnings("deprecation")
	public void showNotification() {
		Notification n = new Notification(
				R.drawable.ic_ssc,
				getString(R.string.informaUploader_title),
				System.currentTimeMillis());
		
		PendingIntent pi = PendingIntent.getActivity(
				this, 
				InformaConstants.Uploader.FROM_NOTIFICATION_BAR, 
				uploadStatus, 
				PendingIntent.FLAG_UPDATE_CURRENT);
		
		n.setLatestEventInfo(this, "uploader!", "hiya!", pi);
		
		nm.notify(R.string.informaUploader_id, n);
	}
	
	private class InformaTrustManager implements X509TrustManager {
		private KeyStore keyStore;
		private X509TrustManager defaultTrustManager;
		private X509TrustManager appTrustManager;
		
		byte[] keyStored = null;
		String pwd;
		long lastUpdate;
		
		public InformaTrustManager() {
			dh.setTable(db, Tables.KEYRING);
			Cursor c = dh.getValue(db, new String[] {Keys.Device.PASSPHRASE, Keys.Device.PUBLIC_KEY}, BaseColumns._ID, 1);
			if(c != null && c.getCount() > 0) {
				c.moveToFirst();
				pwd = c.getString(c.getColumnIndex(Keys.Device.PASSPHRASE));
				c.close();
			}
			
			dh.setTable(db, Tables.KEYSTORE);
			Cursor d = dh.getValue(db, new String[] {
					BaseColumns._ID,
					Keys.TrustedDestinations.CERT, 
					Keys.TrustedDestinations.DATE_UPDATED
				}, BaseColumns._ID, 1);
			if(d != null && d.getCount() > 0) {
				d.moveToFirst();
				keyStored = d.getBlob(d.getColumnIndex(Keys.TrustedDestinations.CERT));
				lastUpdate = d.getLong(d.getColumnIndex(Keys.TrustedDestinations.DATE_UPDATED));
				d.close();
			}
			
			loadKeyStore();
			defaultTrustManager = getTrustManager(false);
			appTrustManager = getTrustManager(true);
		}
		
		private X509TrustManager getTrustManager(boolean withKeystore) {
			try {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
				if(withKeystore)
					tmf.init(keyStore);
				else
					tmf.init((KeyStore) null);
				for(TrustManager t : tmf.getTrustManagers())
					if(t instanceof X509TrustManager)
						return (X509TrustManager) t;
			} catch (KeyStoreException e) {
				Log.e(TAG, "key store exception: " + e.toString());
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, "no such algo exception: " + e.toString());
			}
			return null;
		}
		
		private void loadKeyStore() {
			try {
				keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			} catch(KeyStoreException e) {
				Log.e(TAG, "key store exception: " + e.toString());
			}
			
			try {
				keyStore.load(null, null);
				if(keyStored != null)
					keyStore.load(new ByteArrayInputStream(keyStored), pwd.toCharArray());
			} catch(CertificateException e) {
				Log.e(TAG, "certificate exception: " + e.toString());
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, "no such algo exception: " + e.toString());
			} catch (IOException e) {
				Log.e(TAG, "IOException: " + e.toString());
			}
		}
		
		private void storeCertificate(X509Certificate[] chain) {
			try {
				for(X509Certificate cert : chain)
					keyStore.setCertificateEntry(cert.getSubjectDN().toString(), cert);
			} catch(KeyStoreException e) {
				Log.e(TAG, "keystore exception: " + e.toString());
			}
			
			appTrustManager = getTrustManager(true);
			try {
				//File f = new File(InformaConstants.DUMP_FOLDER, "keys.bks");
				//FileOutputStream fos = new FileOutputStream(f);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				keyStore.store(baos, pwd.toCharArray());
				updateKeyStore(baos.toByteArray());
				Log.d(TAG, "new key encountered!  length: " + baos.size());
			} catch(KeyStoreException e) {
				Log.e(TAG, "keystore exception: " + e.toString());	
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, "no such algo exception: " + e.toString());
			} catch (IOException e) {
				Log.e(TAG, "IOException: " + e.toString());
			} catch (CertificateException e) {
				Log.e(TAG, "Certificate Exception: " + e.toString());
			}
		}
		
		private void updateKeyStore(byte[] newKey) {
			boolean firstKey = (keyStored == null ? true : false);
			keyStored = newKey;
			lastUpdate = System.currentTimeMillis();
			
			dh.setTable(db, Tables.KEYSTORE);
			ContentValues cv = new ContentValues();
			cv.put(TrustedDestinations.CERT, keyStored);
			cv.put(TrustedDestinations.DATE_UPDATED, lastUpdate);
			
			if(firstKey)
				db.insert(dh.getTable(), null, cv);
			else
				db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Long.toString(1)});
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
	
	private class InformaConnectionFactory {
		private HttpsURLConnection connection;
		private URL url;
		private HostnameVerifier hnv;
		private Proxy proxy;
		private DataOutputStream dos;
		String hostString;
		
		private boolean useProxy = false;
		
		public InformaConnectionFactory(final String urlString) throws IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
			this.url = new URL("https://" + urlString);
			hostString = urlString.split("/")[0];
			
			hnv = new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					if(hostname.equals(hostString)) {
						return true;
					} else
						return false;
				}
				
			};
			
			itm = new InformaTrustManager();
			ssl = SSLContext.getInstance("TLS");
			ssl.init(null, new TrustManager[] { itm }, new SecureRandom());
						
			HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hnv);
			if(urlString.contains(".onion")) {
				Log.d(TAG, "this post must be proxied.");
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8118));
				useProxy = true;
			}
		}
		
		public JSONObject executePost(Map<String, Object> nvp, File file, int mediaType)  throws ClientProtocolException, IOException {
			Iterator<Entry<String, Object>> it = nvp.entrySet().iterator();
			
			if(file != null) {
				if(useProxy)
					connection = (HttpsURLConnection) url.openConnection(proxy);
				else
					connection = (HttpsURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Connection","Keep-Alive");
				connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + Keys.Uploader.BOUNDARY);
				connection.setUseCaches(false);
				connection.setDoInput(true);
				connection.setDoOutput(true);
				
				dos = new DataOutputStream(connection.getOutputStream());
				dos.writeBytes(Keys.Uploader.HYPHENS + Keys.Uploader.BOUNDARY + Keys.Uploader.LINE_END);
				dos.writeBytes("Content-Disposition: form-data; name=\"" + Keys.Uploader.Entities.MEDIA_UPLOAD + "\";filename=\"" + file.getName() + "\"" + Keys.Uploader.LINE_END);
				dos.writeBytes("Content-Type: " + (mediaType == MediaTypes.PHOTO ? ObscuraConstants.MIME_TYPE_JPEG : ObscuraConstants.MIME_TYPE_MKV) + Keys.Uploader.LINE_END);
				dos.writeBytes(Keys.Uploader.LINE_END);
				
				FileInputStream fis = new FileInputStream(file);
				byte[] media = new byte[fis.available()];
				fis.read(media);
				
				int index = 0;
				int bufSize = 1024;
				
				do {
					if((index + bufSize) > media.length) {
						bufSize = media.length - index;
					}
					dos.write(media, index, bufSize);
					index += bufSize;
				} while(index < media.length);
				
				dos.writeBytes(Keys.Uploader.LINE_END);
				dos.writeBytes(Keys.Uploader.HYPHENS + Keys.Uploader.BOUNDARY + Keys.Uploader.HYPHENS + Keys.Uploader.LINE_END);
				fis.close();
				
				while(it.hasNext()) {
					Entry<String, Object> e = it.next();
					StringBuilder sb = new StringBuilder();
					sb.append(Keys.Uploader.HYPHENS + Keys.Uploader.BOUNDARY + Keys.Uploader.LINE_END);
					sb.append("Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + Keys.Uploader.LINE_END);
					sb.append("Content-Type: text/plain; charset=UTF-8" + Keys.Uploader.LINE_END + Keys.Uploader.LINE_END);
					
					StringBuilder _sb = new StringBuilder();
					_sb.append(e.getValue());
					sb.append(URLEncoder.encode(_sb.toString(), "UTF-8") + Keys.Uploader.LINE_END);
					
					dos.writeBytes(sb.toString());
					Log.d(TAG, "THIS POST WITH EXTRAS:\n" + sb.toString());
				}
				
				
				
				// ...blah blah blah
			} else {
				StringBuffer sb = new StringBuffer();
				while(it.hasNext()) {
					Entry<String, Object> e = it.next();
					try {
						sb.append("&" + e.getKey() + "=" + URLEncoder.encode((String) e.getValue(), "UTF-8"));
					} catch(ClassCastException cce) {
						sb.append("&" + e.getKey() + "=" + e.getValue());
					}
				}
				
				if(useProxy)
					connection = (HttpsURLConnection) url.openConnection(proxy);
				else
					connection = (HttpsURLConnection) url.openConnection();
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Connection","Keep-Alive");
				connection.setUseCaches(false);
				connection.setDoInput(true);
				connection.setDoOutput(true);
				
				dos = new DataOutputStream(connection.getOutputStream());
				Log.d(TAG, "posting: " + sb.toString().substring(1));
				dos.writeBytes(sb.toString().substring(1));
			}
			
			dos.flush();
			dos.close();
			
			try {
				InputStream is = connection.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line;
				StringBuffer __sb = new StringBuffer();
				while((line = br.readLine()) != null)
					__sb.append(line);
				
				br.close();
				connection.disconnect();
				
				Log.d(TAG, "server returns: " + __sb.toString());
				return (JSONObject) new JSONTokener(__sb.toString()).nextValue();
			} catch(NullPointerException e) {
				Log.e(TAG, "NPE IN THIS LATEST REQUEST: " + e.toString());
				e.printStackTrace();
				return null;
			} catch (JSONException e) {
				return null;
			}
			
		}
		
		public JSONObject executePost(Map<String, Object> nvp) throws ClientProtocolException, IOException {
			return executePost(nvp, null, 0);
		}
	}
}
