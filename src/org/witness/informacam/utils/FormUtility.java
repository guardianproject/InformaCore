package org.witness.informacam.utils;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.odkparser.FormWrapper;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.app.mods.Selections;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Forms;
import org.witness.informacam.utils.Constants.Storage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;

public class FormUtility {
	public static List<String> getAsList(Activity a) {
		List<JSONObject> forms = getAvailableForms();
		if(forms == null)
			return null;

		try {
			List<String> formNames = new ArrayList<String>();
			for(JSONObject j : forms)
				formNames.add(j.getString(Forms.TITLE));

			return formNames;
		} catch(JSONException e) {
			Log.e(Forms.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}

	public static List<JSONObject> getAvailableForms() {
		IOCipherService ioCipher = IOCipherService.getInstance();
		File fManifest = ioCipher.getFile(Storage.IOCipher.FORM_ROOT + "/manifest.json");
		if(!fManifest.exists())
			return null;

		try {
			List<JSONObject> forms_available = new ArrayList<JSONObject>();
			JSONObject manifest = (JSONObject) new JSONTokener(new String(IOUtility.getBytesFromFile(fManifest))).nextValue();
			JSONArray forms_available_ = manifest.getJSONArray("installed_forms");
			for(int i=0; i<forms_available_.length(); i++)
				forms_available.add(forms_available_.getJSONObject(i));
			return forms_available;
		} catch(JSONException e) {
			Log.e(Forms.LOG, e.toString());
			e.printStackTrace();
			return null;
		}
	}
	
	public static Map<Integer, JSONObject> getAnnotationPlugins() {
		return getAnnotationPlugins(0);
	}
	
	@SuppressLint("UseSparseArrays")
	public static Map<Integer, JSONObject> getAnnotationPlugins(int offset) {
		Map<Integer, JSONObject> namespaces = new HashMap<Integer, JSONObject>();
		for(JSONObject form : getAvailableForms())
			namespaces.put(offset, form);
		
		return namespaces;
	}

	public static boolean importAndParse(Activity a, InputStream xml_stream) {
		boolean result = false;
		IOCipherService ioCipher = IOCipherService.getInstance();
		
		File fRoot = ioCipher.getFile(Storage.IOCipher.FORM_ROOT);
		File fManifest = ioCipher.getFile(fRoot, "manifest.json");

		boolean hasFormDef = false;
		File xmlHash = null;
		byte[] xml = null;

		try {
			xml = new byte[xml_stream.available()];
			xml_stream.read(xml);
			xml_stream.close();
			xmlHash = ioCipher.getFile(fRoot, MediaHasher.hash(xml, "MD5") + ".xml");
		} catch (NoSuchAlgorithmException e) {
			Log.e(Constants.Forms.LOG, e.toString());
			e.printStackTrace();
			return result;
		} catch (IOException e) {
			Log.e(Constants.Forms.LOG, e.toString());
			e.printStackTrace();
			return result;
		}


		if(!fRoot.exists())
			fRoot.mkdir();
		else {
			// get saved forms-- do we have this one?
			for(File f : ioCipher.walk(fRoot)) {
				if(xmlHash.getName().equals(f.getName())) {
					hasFormDef = true;
					break;
				}
			}

		}
		
		FormWrapper form_wrapper = new FormWrapper(new ByteArrayInputStream(xml), a, true);
		if(form_wrapper.form_def == null)
			return result;

		if(!hasFormDef) {
			try {
				// form definition should persist
				FileOutputStream fos = new FileOutputStream(xmlHash);
				fos.write(xml);
				fos.flush();
				fos.close();

				JSONObject manifest = new JSONObject();
				List<JSONObject> installed_forms = new ArrayList<JSONObject>();

				if(fManifest.exists()) {
					manifest = (JSONObject) new JSONTokener(new String(IOUtility.getBytesFromFile(fManifest))).nextValue();
					JSONArray installed_forms_ = manifest.getJSONArray("installed_forms");
					for(int i=0; i<installed_forms_.length(); i++)
						installed_forms.add(installed_forms_.getJSONObject(i));

					fManifest.delete();
				}

				JSONObject form = new JSONObject();
				form.put(Forms.TITLE, form_wrapper.form_def.getTitle());
				form.put(Forms.DEF, xmlHash.getAbsolutePath());

				if(!installed_forms.contains(form))
					installed_forms.add(form);

				JSONArray installed_forms_ = new JSONArray();
				for(JSONObject i : installed_forms)
					installed_forms_.put(i);
				manifest.put("installed_forms", installed_forms_);

				Log.d(Forms.LOG, manifest.toString());

				fos = new FileOutputStream(fManifest);
				fos.write(manifest.toString().getBytes());
				fos.flush();
				fos.close();

				result = true;

			} catch(IOException e) {
				Log.e(Constants.Forms.LOG, e.toString());
				e.printStackTrace();
			} catch (JSONException e) {
				Log.e(Constants.Forms.LOG, e.toString());
				e.printStackTrace();
			}
		}

		return result;
	}

	public static boolean importAndParse(Activity a, java.io.File xml) {
		try {
			return importAndParse(a, new FileInputStream(xml));
		} catch (FileNotFoundException e) {
			Log.e(Forms.LOG, e.toString());
			e.printStackTrace();
			return false;
		}
	}

	public static ArrayList<Selections> getAsSelections(Activity a) {
		ArrayList<Selections> forms = null;
		// TODO: this should be a preference...
		//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);

		for(JSONObject j : getAvailableForms()) {
			if(forms == null)
				forms = new ArrayList<Selections>();
			boolean isActive = false;

			try {
				forms.add(new Selections(j.getString(Forms.TITLE), isActive, j));
			} catch(JSONException e) {
				Log.d(Forms.LOG, e.toString());
				e.printStackTrace();
			}
		}


		return forms;
	}
}
