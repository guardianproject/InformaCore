package org.witness.informacam.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.witness.informacam.utils.Constants.Media;

public class Time {
	public final static long timestampToMillis(String ts, String dateFormat) throws ParseException {
		//2012:06:12 10:42:04
		if(dateFormat == null)
			dateFormat = Media.DateFormats.EXIF_DATE_FORMAT;
		
		try {
			DateFormat df = new SimpleDateFormat(dateFormat, Locale.getDefault());
		
			Date d = (Date) df.parse(ts);
			return d.getTime();
		} catch(ParseException e) {
			return Long.parseLong(ts);
		}
	}
	
	public final static long timestampToMillis(String ts) throws ParseException {
		return timestampToMillis(ts, null);
	}
	
	public final static String millisecondsToTimestamp(long ms, long max) {
		return millisecondsToTimestamp(Math.min(ms, max));
	}
	
	public final static String millisecondsToTimestamp(long ms) {
		int s = (int) (ms/1000);
		int hours = s/3600;
		int remainder = s%3600;
		int min = remainder/60;
		int sec = remainder%60;
		
		String ts = ((hours < 10 ? "0" : "") + hours + ":" + (min < 10 ? "0" : "") + min + ":" + (sec < 10 ? "0" : "") + sec);
		if(ts.contains("-"))
			ts = ts.replace("-","0.");
		return ts;
	}
}
