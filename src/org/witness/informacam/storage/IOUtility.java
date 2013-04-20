package org.witness.informacam.storage;

import info.guardianproject.iocipher.File;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.witness.informacam.InformaCam;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.xml.sax.SAXException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Base64;
import android.util.Log;

public class IOUtility {
	private final static String LOG = App.Storage.LOG;

	public static Uri getUriFromFile(Context context, Uri authority, java.io.File file) {
		Uri uri = null;

		ContentResolver cr = context.getContentResolver();
		Cursor c = cr.query(authority, new String[] {BaseColumns._ID}, MediaColumns.DATA + "=?", new String[] {file.getAbsolutePath()}, null);
		if(c != null && c.moveToFirst()) {
			uri = Uri.withAppendedPath(authority, String.valueOf(c.getLong(c.getColumnIndex(BaseColumns._ID))));
			c.close();
		}
		return uri;
	}
	
	public final static JSONObject xmlToJson(InputStream is) {
		
		try {
			JSONObject j = new JSONObject();
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db;
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(is);
			doc.getDocumentElement().normalize();

			NodeList answers = doc.getDocumentElement().getChildNodes();
			Log.d(LOG, "there are " + answers.getLength() + " child nodes");
			for(int n=0; n<answers.getLength(); n++) {
				Node node = answers.item(n);

				Log.d(LOG, "node: " + node.getNodeName());
				if(node.getNodeType() == Node.ELEMENT_NODE) {
					try {
						Element el = (Element) node;
						j.put(node.getNodeName(), el.getFirstChild().getNodeValue());
					} catch(NullPointerException e) {
						continue;
					}
				}
			}
			
			return j;
		} catch (ParserConfigurationException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (DOMException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (JSONException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (SAXException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}

	public final static byte[] getBytesFromBitmap(Bitmap bitmap, boolean asBase64) {
		return getBytesFromBitmap(bitmap, 100, asBase64);
	}

	public final static byte[] getBytesFromBitmap(Bitmap bitmap, int quality, boolean asBase64) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
		if(asBase64) {
			return Base64.encode(baos.toByteArray(), Base64.DEFAULT);
		} else {
			return baos.toByteArray();
		}
	}

	public static byte[] zipBytes(byte[] bytes, String fileName, int source) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();			
			ZipOutputStream zos = new ZipOutputStream(baos);
			ZipEntry ze = new ZipEntry(fileName);
			ze.setSize(bytes.length);
			
			zos.putNextEntry(ze);
			zos.write(bytes);
			zos.closeEntry();
			zos.close();
			
			return baos.toByteArray();
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return null;

	}

	public static boolean zipFiles(Map<String, byte[]> elements, String fileName, int destination) {
		ZipOutputStream zos = null;
		try {
			switch(destination) {
			case Type.IOCIPHER:
				zos = new ZipOutputStream(new info.guardianproject.iocipher.FileOutputStream(fileName));
				break;
			case Type.INTERNAL_STORAGE:
				zos = new ZipOutputStream(new java.io.FileOutputStream(fileName));
				break;
			}

			Iterator<Entry<String, byte[]>> i = elements.entrySet().iterator();
			while(i.hasNext()) {
				Entry<String, byte[]> file = i.next();
				ZipEntry ze = new ZipEntry(file.getKey());
				zos.putNextEntry(ze);

				zos.write(file.getValue());
				zos.flush();
			}

			zos.close();
			return true;
		} catch(IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}

		return false;
	}

	public final static Bitmap getBitmapFromFile(String pathToFile, int source) {
		byte[] bytes = null;
		Bitmap bitmap = null;

		switch(source) {
		case Type.IOCIPHER:
			try {
				info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(pathToFile);
				info.guardianproject.iocipher.FileInputStream fis = new info.guardianproject.iocipher.FileInputStream(file);

				bytes = new byte[fis.available()];
				fis.read(bytes);
				fis.close();

				bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				bytes = null;
			} catch (FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}

			break;
		}

		return bitmap;

	}

	@SuppressWarnings("rawtypes")
	public static List<String> unzipFile (byte[] rawContent, String root, int destination) {
		IOService ioService = InformaCam.getInstance().ioService;
		List<String> paths = new ArrayList<String>();

		String rootFolderPath = "";

		switch(destination) {
		case Type.IOCIPHER:
			info.guardianproject.iocipher.File zf;
			if(root != null) {
				info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(root);
				if(!rootFolder.exists()) {
					rootFolder.mkdir();
				}

				zf = new info.guardianproject.iocipher.File(rootFolder, System.currentTimeMillis() + ".zip");
				rootFolderPath = rootFolder.getAbsolutePath();
			} else {
				zf = new info.guardianproject.iocipher.File(System.currentTimeMillis() + ".zip");
			}

			ioService.saveBlob(rawContent, zf);
			break;
		}

		ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(rawContent));

		ZipEntry entry = null;
		try {
			while((entry = zis.getNextEntry()) != null) {
				boolean isOmitable = false;
				for(String omit : Storage.ICTD.ZIP_OMITABLES) {
					if(entry.getName().contains(omit) || String.valueOf(entry.getName().charAt(0)).compareTo(".") == 0) {
						isOmitable = true;
					}

					if(isOmitable)
						break;
				}

				if(isOmitable)
					continue;

				if(entry.isDirectory()) {
					switch(destination) {
					case Type.IOCIPHER:
						info.guardianproject.iocipher.File rootFolder = new info.guardianproject.iocipher.File(entry.getName());
						if(!rootFolder.exists()) {
							rootFolder.mkdir();
						}

						rootFolderPath = rootFolder.getAbsolutePath();
						break;
					}

					continue;
				}

				BufferedOutputStream bos = null;
				try {
					switch(destination) {
					case Type.IOCIPHER:
						info.guardianproject.iocipher.File entryFile = new info.guardianproject.iocipher.File(rootFolderPath, entry.getName());
						bos = new BufferedOutputStream(new info.guardianproject.iocipher.FileOutputStream(entryFile));
						paths.add(entryFile.getAbsolutePath());
						break;
					}

					byte[] buf = new byte[1024];
					int ch;
					while((ch = zis.read(buf)) > 0) {
						bos.write(buf, 0, ch);
					}

					bos.close();

				} catch (FileNotFoundException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
					return null;
				}
			}


			zis.close();
		} catch (IOException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
			return null;
		}

		return paths;
	}
}
