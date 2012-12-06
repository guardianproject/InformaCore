package org.witness.informacam.informa.forms;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.javarosa.core.model.FormDef;
import org.javarosa.xform.util.XFormUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.app.mods.Selections;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants;
import org.witness.informacam.utils.Constants.Forms;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.MediaHasher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class FormUtils {
	public static List<String> getAsList(Activity a) {
		List<JSONObject> forms = getAvailableForms(a);
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
	
	public static List<JSONObject> getAvailableForms(Activity a) {
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
	
	public static boolean importAndParse(Activity a, java.io.File xml) {
		boolean result = false;
		IOCipherService ioCipher = IOCipherService.getInstance();
		File fRoot = ioCipher.getFile(Storage.IOCipher.FORM_ROOT);
		File fManifest = ioCipher.getFile(fRoot, "manifest.json");

		boolean hasFormDef = false;
		File xmlHash = null;

		try {
			xmlHash = ioCipher.getFile(fRoot, MediaHasher.hash(xml, "MD5") + ".formdef");
		} catch (NoSuchAlgorithmException e) {
			Log.e(Constants.Forms.LOG, e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(Constants.Forms.LOG, e.toString());
			e.printStackTrace();
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

		FormDef fd = null;

		try {
			fd = XFormUtils.getFormFromInputStream(new FileInputStream(xml));
		} catch (FileNotFoundException e) {
			Log.e(Constants.Forms.LOG, e.toString());
			e.printStackTrace();
		}

		if(fd == null)
			return result;

		if(!hasFormDef) {
			try {
				// form definition should persist
				FileOutputStream fos = new FileOutputStream(xmlHash);
				DataOutputStream dos = new DataOutputStream(fos);
				fd.writeExternal(dos);
				dos.flush();
				dos.close();

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
				form.put(Forms.TITLE, fd.getTitle());
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

	public static ArrayList<Selections> getAsSelections(Activity a) {
		ArrayList<Selections> forms = null;
		// TODO: this should be a preference...
		//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);
		
		for(JSONObject j : getAvailableForms(a)) {
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
