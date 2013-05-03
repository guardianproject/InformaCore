package org.witness.informacam.utils;

import info.guardianproject.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MediaHasher 
{
	private final static int BYTE_READ_SIZE = 8192;

	public static String hash (File file, String hashFunction)  throws IOException, NoSuchAlgorithmException
	{
		return hash (new FileInputStream(file), hashFunction);
	}
	
	public static String hash (byte[] bytes, String hashFunction) throws NoSuchAlgorithmException, IOException
	{
		return hash (new ByteArrayInputStream(bytes), hashFunction);
	}
	
	public static String hash (InputStream is, String hashFunction) throws IOException, NoSuchAlgorithmException
	{
		MessageDigest digester;
		
		digester = MessageDigest.getInstance(hashFunction); //MD5 or SHA-1
	
		  byte[] bytes = new byte[BYTE_READ_SIZE];
		  int byteCount;
		  while ((byteCount = is.read(bytes)) > 0) {
		    digester.update(bytes, 0, byteCount);
		  }
		  
		  byte[] messageDigest = digester.digest();
		  
		// Create Hex String WTF?!
		  	/*
	        StringBuffer hexString = new StringBuffer();
	        for (int i=0; i<messageDigest.length; i++)
	            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
	        */
	        
	        return new String(Hex.encode(messageDigest), Charset.forName("UTF-8"));
	
	}
	
	public static String getBitmapHash(Bitmap bitmap) throws NoSuchAlgorithmException, IOException {
		MessageDigest digester = MessageDigest.getInstance("SHA-1");
		
		for(int h=0; h<bitmap.getHeight(); h++) {
			int[] row = new int[bitmap.getWidth()];
			bitmap.getPixels(row, 0, row.length, 0, h, row.length, 1);
			
			byte[] rowBytes = new byte[row.length];
			for(int b=0; b<row.length; b++) {
				rowBytes[b] = (byte) row[b];
			}
			
			digester.update(rowBytes);
			rowBytes = null;
			row = null;
			
		}
		
		byte[] messageDigest = digester.digest();
		return new String(Hex.encode(messageDigest), Charset.forName("UTF-8"));
	}
	
	public static String getBitmapHash(java.io.File file) throws NoSuchAlgorithmException, IOException {
		Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
		String hash = "";
		ByteBuffer buf;
		
		buf = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
		
		bitmap.copyPixelsToBuffer(buf);
		hash = MediaHasher.hash(buf.array(), "SHA-1");
		buf.clear();
		buf = null;
		return hash;
	}
	
	public static String getBitmapHash(info.guardianproject.iocipher.FileInputStream fis) throws NoSuchAlgorithmException, IOException {
		Bitmap bitmap = BitmapFactory.decodeStream(fis);
		String hash = "";
		ByteBuffer buf;
		
		buf = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
		
		bitmap.copyPixelsToBuffer(buf);
		hash = MediaHasher.hash(buf.array(), "SHA-1");
		buf.clear();
		buf = null;
		return hash;
	}
	
	public static String getBitmapHash(java.io.FileInputStream fis) throws NoSuchAlgorithmException, IOException {
		Bitmap bitmap = BitmapFactory.decodeStream(fis);
		String hash = "";
		ByteBuffer buf;
		
		buf = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
		
		bitmap.copyPixelsToBuffer(buf);
		hash = MediaHasher.hash(buf.array(), "SHA-1");
		buf.clear();
		buf = null;
		return hash;
	}
	
}