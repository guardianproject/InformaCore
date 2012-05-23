package org.witness.informa;

import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.witness.informa.utils.ImageConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Image;
import org.witness.informa.utils.InformaConstants.Keys.Settings;
import org.witness.informa.utils.InformaConstants.Keys.Tables;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.DestoService;
import org.witness.mods.InformaTextView;
import org.witness.ssc.R;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

public class EncryptActivity extends Activity {
	InformaTextView progress;
	String clonePath;
	ArrayList<Map<Long, String>> metadataToEncrypt;
	ArrayList<MetadataPack> metadataPacks;
	ArrayList<String> unknownDestos;
	
	private DatabaseHelper dh;
	private SQLiteDatabase db;
	DestoService destos;
	SharedPreferences sp;
	int encrypted = 0;
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.encrypt_activity);
		
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		clonePath = getIntent().getStringExtra(Keys.Service.CLONE_PATH);
		metadataToEncrypt = (ArrayList<Map<Long, String>>) getIntent().getSerializableExtra(Keys.Service.ENCRYPT_METADATA);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(sp.getString(Settings.HAS_DB_PASSWORD, ""));
		
		metadataPacks = new ArrayList<MetadataPack>();
		
		for(Map<Long, String> mo : metadataToEncrypt) {
			dh.setTable(db, Tables.IMAGES);
			Entry<Long, String> e = mo.entrySet().iterator().next();
			Cursor img = dh.getValue(db, new String[] {Image.METADATA, Image.TRUSTED_DESTINATION}, BaseColumns._ID, (Long) e.getKey());
			if(img != null && img.getCount() == 1) {
				img.moveToFirst();
				
				dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
				Cursor td = dh.getValue(db, new String[] {TrustedDestinations.DESTO}, TrustedDestinations.EMAIL, img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)));
				if(td != null && td.getCount() == 1) {
					td.moveToFirst();
					if(td.getString(td.getColumnIndex(TrustedDestinations.DESTO)) == null)
						addDesto(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)));
				} else {
					addDesto(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)));
				}
				
				metadataPacks.add(new MetadataPack(img.getString(img.getColumnIndex(Image.TRUSTED_DESTINATION)), img.getString(img.getColumnIndex(Image.METADATA)), e.getValue()));
				td.close();
					
			}
			img.close();
		}
		
		if(unknownDestos != null) {
			try {
				getDestos();
			} catch (JSONException e) {
				Log.e(InformaConstants.TAG, "could not get destos: " + e.toString());
			}
		}
			
		
		// long id of metadatablob, filename
		
		Log.d(InformaConstants.TAG, metadataToEncrypt.toString());
		progress = (InformaTextView) findViewById(R.id.encrypting_progress);
		
		doEncrypt();
		
	}
	
	private void doEncrypt() {
		for(MetadataPack mp : metadataPacks) {
			if(mp.tdDestination != null) {
				progress.setText(getResources().getString(R.string.apg_encrypting_progress) + " " + mp.email + "\u2026");
				PasswordService pwd = new PasswordService(mp.tdDestination);
				try {
					SecureRandom sr = new SecureRandom();
					byte[] salt = new byte[20];
					sr.nextBytes(salt);
					SecretKeyFactory fac = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
					KeySpec ks = new PBEKeySpec(pwd.getPassword(), salt, 65536, 256);
					SecretKey secret = new SecretKeySpec(fac.generateSecret(ks).getEncoded(), "AES");
					
					Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
					cipher.init(Cipher.ENCRYPT_MODE, secret);
					AlgorithmParameters ap = cipher.getParameters();
					
					// TRANSMITTED VIA HTTPS REQUEST with the PASSWORD!
					byte[] iv = ap.getParameterSpec(IvParameterSpec.class).getIV();
					
					byte[] ciphertext = cipher.doFinal(mp.metadata.getBytes("UTF-8"));
					mp.metadata = new String(ciphertext);
					
					if(ImageConstructor.insertMetadata(mp) != 0) {
						//TODO: upload! GO!
						Log.d(InformaConstants.TAG, "uploading to " + mp.tdDestination);
					}
					
				} catch (NoSuchAlgorithmException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (InvalidKeySpecException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (NoSuchPaddingException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (InvalidKeyException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (InvalidParameterSpecException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (IllegalBlockSizeException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (BadPaddingException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				} catch (UnsupportedEncodingException e) {
					Log.e(InformaConstants.TAG, "keygen error: " + e.toString());
				}
				
				
			}
		}
	}
	
	private void addDesto(String desto) {
		if(unknownDestos == null)
			unknownDestos = new ArrayList<String>();
		
		Log.d(InformaConstants.TAG, "there is no desto for " + desto);
		unknownDestos.add(desto);
	}
	
	private void getDestos() throws JSONException {
		DestoService ds = new DestoService(unknownDestos);
		ArrayList<Map<String, String>> destos = ds.getDestos();
		if(destos != null) {
			if(destos.size() > 0) {
				dh.setTable(db, Tables.TRUSTED_DESTINATIONS);
				for(Map<String, String> desto : destos) {
					Log.d(InformaConstants.TAG, "this desto: " + desto.toString());
					Entry<String, String> e = desto.entrySet().iterator().next();
					ContentValues cv = new ContentValues();
					cv.put(TrustedDestinations.DESTO, e.getValue());
					db.update(dh.getTable(), cv, TrustedDestinations.EMAIL + " = ?", new String[] {e.getKey()});
					for(MetadataPack mp : metadataPacks)
						if(mp.email.equals(e.getKey()))
							mp.setTDDestination(e.getValue());
				}
			}
		}
	}
	
	@Override
	public void onStop() {
		super.onStop();
		db.close();
		dh.close();
	}
	
	public class MetadataPack {
		public String email, metadata, filepath, clonepath;
		public String tdDestination = null;
		
		public MetadataPack(String email, String metadata, String filepath) {
			this.email = email;
			this.metadata = metadata;
			this.filepath = filepath;
			this.clonepath = EncryptActivity.this.clonePath;
		}
		
		public void setTDDestination(String tdDestination) {
			this.tdDestination = tdDestination;
		}
	}
	
	private class PasswordService {
		private final String push;
		
		public PasswordService(String push) {
			this.push = push;
		}
		
		public char[] getPassword() {
			
			return new String("pwd").toCharArray();
		}
	}	
}
