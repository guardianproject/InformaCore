package org.witness.informacam.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
		  
		// Create Hex String
	        StringBuffer hexString = new StringBuffer();
	        for (int i=0; i<messageDigest.length; i++)
	            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
	        return hexString.toString();
	
	}
	
	public static String getBitmapHash(File file) throws NoSuchAlgorithmException, IOException {
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
	
	public static String getBitmapHash(FileInputStream fis) throws NoSuchAlgorithmException, IOException {
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