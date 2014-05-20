package org.witness.informacam.models.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.hardware.SensorManager;
import android.util.Log;

public class PressureServiceUpdater {

	 private float sea_level_pressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
	  
	  public static final String TAG_PRESSURE = "aws:pressure";
	  public static final String TAG_LONGITUDE = "aws:longitude";
	  public static final String TAG_LATITUDE = "aws:latitude";

	  private final static String API_WXBUG = "TODO";
	  
	  public void updatePressure(final double longitude, final double latitude){
	    Thread task = new Thread(new Runnable(){
	      public void run() {
	        sea_level_pressure = GetRefPressureWxbug(longitude,latitude);
	        Log.d("Pressure","updated! " + sea_level_pressure);
	      }});
	    task.start();
	  }  // this function is
	  
	  public float getSeaLevelPressure ()
	  {
		  return sea_level_pressure;
	  }
	  
	  public float GetRefPressureWxbug(double longitude, double latitude) {
	    InputStream is = null;
	    float pressure = 0.0f;
	    try {
	      String strUrl = String.format(Locale.US,"http://" + API_WXBUG + ".api.wxbug.net/getLiveWeatherRSS.aspx?ACode=" + API_WXBUG + "&lat=%f&long=%f&UnitType=1&OutputType=1",latitude,longitude );
	      URL text = new URL(strUrl);
	      URLConnection connection = text.openConnection();
	      connection.setReadTimeout(30000);
	      connection.setConnectTimeout(30000);

	      is = connection.getInputStream();
	      
	      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	      DocumentBuilder domParser = dbf.newDocumentBuilder();
	      Document xmldoc = domParser.parse(is);
	      Element root = xmldoc.getDocumentElement();
	      
	      pressure = Float.parseFloat(getTagValue(TAG_PRESSURE, root));
	      //float lon = Float.parseFloat(getTagValue(TAG_LONGITUDE, root));
	      //float lat = Float.parseFloat(getTagValue(TAG_LATITUDE, root));
	    } catch (Exception e) {
	      Log.e("Pressure", "Error in network call", e);
	      pressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
	    } finally {
	      try {
	        if(is!=null)
	          is.close(); 
	      } catch (IOException e) {
	        e.printStackTrace();
	      }
	    }
	    return pressure;
	  }
	  
	  private String getTagValue(String sTag, Element eElement) {
	    NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
	    Node nValue = (Node) nlList.item(0);
	    return nValue.getNodeValue();
	  }
}