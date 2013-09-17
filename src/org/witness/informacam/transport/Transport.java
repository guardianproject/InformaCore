package org.witness.informacam.transport;

import info.guardianproject.onionkit.ui.OrbotHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.organizations.IRepository;
import org.witness.informacam.models.transport.ITransportStub;
import org.witness.informacam.models.transport.ITransportData;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;

import ch.boye.httpclientandroidlib.NameValuePair;
import ch.boye.httpclientandroidlib.client.utils.URLEncodedUtils;
import ch.boye.httpclientandroidlib.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class Transport extends IntentService {
	ITransportStub transportStub;
	IRepository repository;
	String repoName;
	
	InformaCam informaCam;
	
	protected final static String LOG = "************************** TRANSPORT **************************";
	
	private final static String URL_USE_TOR_STRING = ".onion"; //if you see this in the url string, use the local Tor proxy
	
	public Transport(String name) {
		super(name);
		
		this.repoName = name;
		Logger.d(LOG, this.repoName);
		informaCam = InformaCam.getInstance();
	}
	
	protected boolean init() {
		repository = transportStub.getRepository(repoName);
		
		int transportRequirements = checkTransportRequirements();
		if(transportRequirements == -1) {
			transportStub.numTries++;
		} else {
			transportStub.numTries = (Models.ITransportStub.MAX_TRIES + 1);
			Logger.d(LOG, "ACTUALLY NO ORBOT");
			
			finishUnsuccessfully(transportRequirements);
			// Prompt to start up/install orbot here.
			
			
			stopSelf();
			return false;
		}
		
		return true;
	}
	
	protected void send() {}
	
	protected void finishSuccessfully() {
		transportStub.resultCode = Models.ITransportStub.ResultCodes.OK;
		
		if(transportStub.associatedNotification != null) {
			transportStub.associatedNotification.taskComplete = true;
			informaCam.updateNotification(transportStub.associatedNotification, informaCam.h);
		}
		
		stopSelf();
		
	}
	
	protected void finishUnsuccessfully() {
		finishUnsuccessfully(-1);
	}
	
	protected void finishUnsuccessfully(int transportRequirements) {
		if(informaCam.getEventListener() != null) {
			Message message = new Message();
			Bundle data = new Bundle();
			
			if(transportRequirements == -1) {
				data.putInt(Codes.Extras.MESSAGE_CODE, Codes.Messages.Transport.GENERAL_FAILURE);
				data.putString(Codes.Extras.GENERAL_FAILURE, informaCam.getString(R.string.informacam_could_not_send));
			} else {
				data.putInt(Codes.Extras.MESSAGE_CODE, transportRequirements);
			}
			
			message.setData(data);


			informaCam.getEventListener().onUpdate(message);
		}
		
		if(transportStub.associatedNotification != null) {
			transportStub.associatedNotification.canRetry = true;
			transportStub.associatedNotification.save();
			
			informaCam.transportManifest.add(transportStub);
		}
		
		
	}
	
	public int checkTransportRequirements () {
		if(repository.asset_root != null && repository.asset_root.toLowerCase().contains(".onion")) {
			OrbotHelper oh = new OrbotHelper(this);
			
			if(!oh.isOrbotInstalled()) {
				return Codes.Messages.Transport.ORBOT_UNINSTALLED;
			} else if(!oh.isOrbotRunning()) {
				return Codes.Messages.Transport.ORBOT_NOT_RUNNING;
			}
		}
	
		return -1;
	}
	
	protected void resend() {
		if(transportStub.numTries <= Models.ITransportStub.MAX_TRIES) {
			Logger.d(LOG, "POST FAILED.  Trying again.");
			init();
		} else {
			finishUnsuccessfully();
			stopSelf();
		}
	}
	
	@SuppressLint("DefaultLocale")
	protected Object doPost(ITransportData fileData, String urlString) {
		boolean useTorProxy = false;
		
		if (urlString.toLowerCase().contains(URL_USE_TOR_STRING))
			useTorProxy = true;
		
		HttpURLConnection http = buildConnection(urlString, useTorProxy);
		
		try {
			http.setDoOutput(true);
			http.setRequestMethod("POST");
			http.setRequestProperty("Content-Type", fileData.mimeType);
			http.setRequestProperty("Content-Disposition", "attachment; filename=\"" + fileData.assetName + "\"");
			//http.getOutputStream().write(informaCam.ioService.getBytes(fileData.assetPath, Type.IOCIPHER));
			
			InputStream in = informaCam.ioService.getStream(fileData.assetPath, Type.IOCIPHER);
			BufferedOutputStream out = new BufferedOutputStream(http.getOutputStream());
			
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
			   out.write(buffer, 0, len);
			}
			out.flush();
			
			in.close();
			out.close();
			
			InputStream is = new BufferedInputStream(http.getInputStream());
			http.connect();
			
			Logger.d(LOG, "RESPONSE CODE: " + http.getResponseCode());
			Logger.d(LOG, "RESPONSE MSG: " + http.getResponseMessage());
			
			if(http.getResponseCode() > -1) {
				return(parseResponse(is));
			}
			
		} catch (ProtocolException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}
	
	protected Object doPost(Model postData, String urlString) {
		
		boolean useTorProxy = false;
		
		if (urlString.toLowerCase().contains(URL_USE_TOR_STRING))
			useTorProxy = true;
		
		HttpURLConnection http = buildConnection(urlString, useTorProxy);
			
		try {
			http.setDoOutput(true);
			http.setRequestMethod("POST");
			http.setRequestProperty("Content-Type", MimeType.JSON);
			http.getOutputStream().write(postData.asJson().toString().getBytes());
			
			InputStream is = new BufferedInputStream(http.getInputStream());
			http.connect();
			
			Logger.d(LOG, "RESPONSE CODE: " + http.getResponseCode());
			Logger.d(LOG, "RESPONSE MSG: " + http.getResponseMessage());
			
			if(http.getResponseCode() > -1) {
				return(parseResponse(is));
			}
			
		} catch (ProtocolException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}
	
	protected Object doPut(Model putData, String urlString) {
	
		boolean useTorProxy = false;
		
		if (urlString.toLowerCase().contains(URL_USE_TOR_STRING))
			useTorProxy = true;
		
		HttpURLConnection http = buildConnection(urlString, useTorProxy);
		
		try {
			//http.setDoOutput(true);
			http.setRequestMethod("PUT");
			http.setRequestProperty("Content-Type", MimeType.JSON);
			http.getOutputStream().write(putData.asJson().toString().getBytes());
			
			http.connect();
			
			Logger.d(LOG, "RESPONSE CODE: " + http.getResponseCode());
			Logger.d(LOG, "RESPONSE MSG: " + http.getResponseMessage());
			
			if(http.getResponseCode() > -1) {
				InputStream is = new BufferedInputStream(http.getInputStream());
				
				return(parseResponse(is));
			}
			
		} catch (ProtocolException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}
	
	protected Object doGet(String urlString) {
		return doGet(null, urlString);
	}
	
	@SuppressWarnings("unchecked")
	protected Object doGet(Model getData, String urlString) {
		if(getData != null) {
			List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
			Iterator<String> it = getData.asJson().keys();
			while(it.hasNext()) {
				String key = it.next();
				try {
					// XXX: *IF* value is not nothing.
					nameValuePair.add(new BasicNameValuePair(key, String.valueOf(getData.asJson().get(key))));
				} catch (JSONException e) {
					Logger.e(LOG, e);
				}
			}
			urlString += ("?" + URLEncodedUtils.format(nameValuePair, "utf_8"));
		}
		
		boolean useTorProxy = false;
		
		if (urlString.toLowerCase().contains(URL_USE_TOR_STRING))
			useTorProxy = true;
		
		HttpURLConnection http = buildConnection(urlString, useTorProxy);
		
		try {
			http.setRequestMethod("GET");
			http.setDoOutput(false);
			
			InputStream is = new BufferedInputStream(http.getInputStream());
			http.connect();
			
			Logger.d(LOG, "RESPONSE CODE: " + http.getResponseCode());
			Logger.d(LOG, "RESPONSE MSG: " + http.getResponseMessage());
			
			if(http.getResponseCode() > -1) {
				return(parseResponse(is));
			} else {
				try {
					Logger.d(LOG, String.format(LOG, "ERROR IF PRESENT:\n%s", ((JSONObject) parseResponse(is)).toString()));
				} catch(Exception e) {
					Logger.e(LOG, e);
				}
			}
			
		} catch (ProtocolException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}
	
	protected Object parseResponse(InputStream is) {
		StringBuffer lastResult = new StringBuffer();
		
		try {
			for(String line : IOUtils.readLines(is)) {
				Logger.d(LOG, line);
				lastResult.append(line);
			}
			
			transportStub.lastResult = lastResult.toString();
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return null;
	}
	
	private HttpURLConnection buildConnection(String urlString, boolean useTorProxy) {
		HttpURLConnection http = null;
		Logger.d(LOG, "LETS CONNECT TO " + (urlString == null ? repository.asset_root : urlString));
		
		try {
			URL url = new URL(urlString == null ? repository.asset_root : urlString);
			
			Logger.d(LOG,  "URL PROTOCOL: " + url.getProtocol());
			if(url.getProtocol().equals("https")) {	
				// TODO: add memorizing trust manager
			}
			
			if (useTorProxy)
			{
				Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8118));
				http = (HttpURLConnection) url.openConnection(proxy);
			}
			
			http.setUseCaches(false);
			
		} catch (MalformedURLException e) {
			Logger.e(LOG, e);
		} catch (IOException e) {
			Logger.e(LOG, e);
		}
		
		return http;
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		Logger.d(LOG, "onHandleIntent called");
				
		transportStub = (ITransportStub) intent.getSerializableExtra(Models.ITransportStub.TAG);
		Log.d(LOG, "TRANSPORT:\n" + transportStub.asJson().toString()); 
		
		if(transportStub == null) {
			stopSelf();
		} else {
			init();
		}
	}
}
