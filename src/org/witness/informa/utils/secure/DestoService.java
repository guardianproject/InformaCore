package org.witness.informa.utils.secure;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
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
import javax.net.ssl.X509TrustManager;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informa.EncryptActivity.MetadataPack;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Media;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.InformaConstants.Media.ShareVector;
import org.witness.informa.utils.InformaConstants.Media.Status;
import org.witness.informa.utils.io.DatabaseHelper;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

/*
 * This class takes an array of email addresses
 * and returns a mapping of emails and desto URIs
 * to calling function.
 * 
 * If any desto is not found in the database,
 * it will look up the desto's URI at the GuardianProject repo 
 */
public class DestoService {
	private StringBuffer destoQuery;
	DatabaseHelper dh;
	SQLiteDatabase db;
	SharedPreferences sp;
	private ArrayList<Map<String, String>> destoResults;
	ArrayList<Map<String, Long>> destos;
	
	public DestoService(Context c) {
		sp = PreferenceManager.getDefaultSharedPreferences(c);
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(sp.getString(Keys.Settings.HAS_DB_PASSWORD, ""));
		
		destoResults = new ArrayList<Map<String, String>>();
		destoQuery = new StringBuffer();
	}
	
	private boolean isLocallyAvailable(String email) {
		dh.setTable(this.db, Tables.TRUSTED_DESTINATIONS);
		Cursor td = dh.getValue(db, new String[] {TrustedDestinations.DESTO}, TrustedDestinations.EMAIL, email);
		if(td != null && td.getCount() == 1) {
			td.moveToFirst();
			if(td.getString(td.getColumnIndex(TrustedDestinations.DESTO)) == null) {
				Log.d(InformaConstants.TAG, "DESTO SERVICE: contact is null");
				td.close();
				return false;
			} else {
				Map<String, String> desto = new HashMap<String, String>();
				desto.put(email, td.getString(td.getColumnIndex(TrustedDestinations.DESTO)));
				destoResults.add(desto);
				td.close();
				return true;
			}
		} else {
			return false;
		}
	}
		
	public ArrayList<Map<String, String>> getDestos(ArrayList<Map<String, Long>> destos) throws JSONException {
		this.destos = destos;
		for(Map<String, Long> d : this.destos) {
			Entry<String, Long> desto = d.entrySet().iterator().next();
			if(!isLocallyAvailable(desto.getKey()))
				destoQuery.append("," + desto.getKey());
		}
		
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
					URL url = new URL(InformaConstants.REPO + destoQuery.toString().substring(1));
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
		
		Log.d(InformaConstants.TAG, "DESTO SERVICE: destoQuery:" + destoQuery.toString()); 
		if(destoQuery.length() != 0) {
			try {
				String destoResponse = f.get();
				Log.d(InformaConstants.TAG, "DESTO SERVICE: destoResponse: " + destoResponse);
				JSONArray jsond = (JSONArray) ((JSONObject) new JSONTokener(destoResponse).nextValue()).get(TrustedDestinations.HOOKUPS);
				for(int i=0; i<jsond.length(); i++) {
					Map<String, String> desto = new HashMap<String, String>();
					JSONObject destoj = (JSONObject) jsond.get(i);
					desto.put(destoj.getString(TrustedDestinations.EMAIL), destoj.getString(TrustedDestinations.DESTO));
					updateTrustedDestination(desto);
					destoResults.add(desto);
				}
			} catch(InterruptedException e) {
				Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			} catch(ExecutionException e) {
				Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			} catch(NullPointerException e) {
				Log.e(InformaConstants.TAG, "https error in getDestos(): " + e.toString());
			}
		}
		
		for(Map<String, Long> d : this.destos) {
			Entry<String, Long> desto = d.entrySet().iterator().next();
			if(!isLocallyAvailable(desto.getKey()))
				setShareVector(desto.getValue(), ShareVector.ENCRYPTED_BUT_NOT_UPLOADED, Status.NEVER_SCHEDULED_FOR_UPLOAD);
			else
				setShareVector(desto.getValue(), ShareVector.ENCRYPTED_UPLOAD_QUEUE, Status.UPLOADING);
		}
		
		db.close();
		dh.close();
		return destoResults;
	}
	
	private void setShareVector(long record, int shareVector, int status) {
		dh.setTable(db, Tables.IMAGES);
		ContentValues cv = new ContentValues();
		cv.put(Keys.Media.SHARE_VECTOR, shareVector);
		cv.put(Keys.Media.STATUS, status);
		db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Long.toString(record)});
	}
	
	private void updateTrustedDestination(Map<String, String> desto) {
		dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
		Entry<String, String> e = desto.entrySet().iterator().next();
		ContentValues cv = new ContentValues();
		cv.put(TrustedDestinations.DESTO, e.getValue());	
		db.update(dh.getTable(), cv, TrustedDestinations.EMAIL + " = ?", new String[] {e.getKey()});
	}
}
