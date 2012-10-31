package org.witness.informacam.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.WizardActivity;
import org.witness.informacam.app.mods.Selections;
import org.witness.informacam.crypto.CertificateUtility;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.crypto.CertificateUtility.ClientCertificateResponse;
import org.witness.informacam.crypto.KeyUtility.KeyServerResponse;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;

import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.util.Log;

public class AddressBookUtility {
	public interface AddressBookListener {
		public void receiveNewAddresses(AddressBookDisplay address);
		public void onNoResultFound(String emailAddress);
		public void onMultipleChoicesFound(ArrayList<Selections> choices);
	}

	public static void getLookupFromEmailAddress(final Activity c, String lookup) {
		String[] projection = new String[] {
				Contacts.DISPLAY_NAME,
				Email.CONTACT_ID
		};

		AddressBookDisplay abd = null;
		long contactId = 0L;
		String contactDisplayName = null;

		Cursor a = c.getContentResolver().query(Email.CONTENT_URI, projection, Email.DATA + "=?", new String[] {lookup}, null);
		if(a != null && a.moveToFirst()) {
			contactId = a.getLong(a.getColumnIndex(Email.CONTACT_ID));
			contactDisplayName = a.getString(a.getColumnIndex(Contacts.DISPLAY_NAME));

			String photoQuery = Data.MIMETYPE + "='" + Photo.CONTENT_ITEM_TYPE + "'";
			Cursor b = c.getContentResolver().query(Data.CONTENT_URI, null, photoQuery, null, null);

			abd = new AddressBookDisplay(c, contactId, contactDisplayName, lookup, null, true);
			if(b != null) {
				if(b.moveToFirst()) {
					Uri contact = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);										
					Uri photoUri = Uri.withAppendedPath(contact, Contacts.Photo.CONTENT_DIRECTORY);

					Cursor p = c.getContentResolver().query(photoUri, new String[] {Contacts.Photo.PHOTO}, null, null, null);
					if(p != null) {
						if(p.moveToFirst())
							abd = new AddressBookDisplay(c, contactId, contactDisplayName, lookup, Base64.encode(p.getBlob(0)), true);
						p.close();
					} 

				}
				b.close();
			}
			a.close();
		}

		if(abd != null)
			((AddressBookListener) c).receiveNewAddresses(abd);
		else
			((AddressBookListener) c).onNoResultFound(lookup);

	}

	public static void getEmailAddressFromLookup(final Activity c, long lookup) {
		AddressBookUtility.getEmailAddressFromLookup(c, lookup, -1);
	}

	public static void getEmailAddressFromLookup(final Activity c, long lookup, int certainChoice) {
		String[] projection = new String[] {
				Email.DATA,
				Contacts.DISPLAY_NAME,
		};

		AddressBookDisplay abd = null;
		String contactEmail = "email";
		String contactDisplayName = null;

		int choice = 0;

		Cursor a = c.getContentResolver().query(Email.CONTENT_URI, projection, Email.CONTACT_ID + "=?", new String[] {Long.toString(lookup)}, null);
		if(a != null && a.moveToFirst()) {
			if(a.getCount() > 1) {
				if(certainChoice == -1) {
					ArrayList<Selections> choices = new ArrayList<Selections>();
					choices.add(new Selections(a.getString(a.getColumnIndex(Email.DATA)), false));
					while(a.moveToNext())
						choices.add(new Selections(a.getString(a.getColumnIndex(Email.DATA)), false));
					((AddressBookListener) c).onMultipleChoicesFound(choices);

					return;
				} else
					choice = certainChoice;
			}

			a.moveToPosition(choice);
			contactEmail = a.getString(a.getColumnIndex(Email.DATA));
			contactDisplayName = a.getString(a.getColumnIndex(Contacts.DISPLAY_NAME));

			String photoQuery = Data.MIMETYPE + "='" + Photo.CONTENT_ITEM_TYPE + "'";
			Cursor b = c.getContentResolver().query(Data.CONTENT_URI, null, photoQuery, null, null);

			abd = new AddressBookDisplay(c, lookup, contactDisplayName, contactEmail, null, true);
			if(b != null) {
				if(b.moveToFirst()) {
					Uri contact = ContentUris.withAppendedId(Contacts.CONTENT_URI, lookup);										
					Uri photoUri = Uri.withAppendedPath(contact, Contacts.Photo.CONTENT_DIRECTORY);

					Cursor p = c.getContentResolver().query(photoUri, new String[] {Contacts.Photo.PHOTO}, null, null, null);
					if(p != null) {
						if(p.moveToFirst())
							abd = new AddressBookDisplay(c, lookup, contactDisplayName, contactEmail, Base64.encode(p.getBlob(0)), true);
						p.close();
					} 

				}
				b.close();
			}
			a.close();
		}

		if(abd != null)
			((AddressBookListener) c).receiveNewAddresses(abd);
		else
			((AddressBookListener) c).onNoResultFound(contactEmail);
	}

	public static void deleteContact(long tdId, String email) {
		DatabaseHelper dh = DatabaseService.getInstance().getHelper();
		SQLiteDatabase db = DatabaseService.getInstance().getDb();
		
		long keyringId = 0L;
		
		dh.setTable(db, Tables.Keys.TRUSTED_DESTINATIONS);
		Cursor c = dh.getValue(db, new String[] {TrustedDestination.Keys.KEYRING_ID}, BaseColumns._ID, tdId);
		if(c != null && c.moveToFirst()) {
			keyringId = c.getLong(c.getColumnIndex(TrustedDestination.Keys.KEYRING_ID));
			c.close();
		}
		
		dh.removeValue(db, new String[] {BaseColumns._ID}, new Long[] {tdId});
		dh.setTable(db, Tables.Keys.KEYRING);
		dh.removeValue(db, new String[] {TrustedDestination.Keys.KEYRING_ID}, new Long[] {keyringId});	
	}

	public static boolean installICTDPackage(Activity a, File _ictd, boolean isEncrypted) {
		boolean result = false;
		File ictd = _ictd;
		File encryptedICTD = null;
		
		if(isEncrypted) {
			// decrypt file
			ictd = KeyUtility.decrypt(ictd);
			encryptedICTD = ictd;
		}

		// unzip file to a folder
		ictd = (java.io.File) IOUtility.unzip(ictd);

		// install keys, etc
		KeyServerResponse ksr = null;
		byte[] imgBytes = null;
		byte[] certBytes = null;
		byte[] pgpBytes = null;
		String trustedDestinationURL = null;
		String clientPassword = null;
		ClientCertificateResponse ccr = null;
		
		DatabaseHelper dh = DatabaseService.getInstance().getHelper();
		SQLiteDatabase db = DatabaseService.getInstance().getDb();

		for(File keyFile : ictd.listFiles()[0].listFiles()) {
			
			String ext = null;
			
			try {
				ext = keyFile.getName().substring(keyFile.getName().lastIndexOf("."));

			} catch(StringIndexOutOfBoundsException e) {
				continue;
			}

			if(ext.equals(".txt")) {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(keyFile)));
					char[] buf = new char[1024];
					int numRead = 0;

					String line;
					StringBuilder sb = new StringBuilder();

					while((numRead = br.read(buf)) != -1) {
						line = String.valueOf(buf, 0, numRead);

						String[] lines = line.split(";");
						for(String l : lines) {
							String key = l.split("=")[0];
							String value = l.split("=")[1];

							if(key.equals(TrustedDestination.Keys.URL))
								trustedDestinationURL = value;
							if(key.equals(Crypto.Keystore.Keys.PASSWORD))
								clientPassword = value;

							// TODO: if we add other key-values to the txt file, they can be caught here...
						}

						buf = new char[1024];
					}

					br.close();
				} catch(IOException e) {
					Log.e(Storage.LOG, e.toString());
					e.printStackTrace();
				}

			} else {
				InputStream is = null;
				
				try {
					is = new FileInputStream(keyFile);
				} catch (FileNotFoundException e1) {
					Log.e(Storage.LOG, e1.toString());
					e1.printStackTrace();
				}
				
				if(ext.equals(".png") || ext.equals(".jpg")) {
					try {
						imgBytes = new byte[is.available()];
						is.read(imgBytes);
						imgBytes = android.util.Base64.encode(imgBytes, android.util.Base64.DEFAULT);
					} catch (IOException e) {
						Log.e(Storage.LOG, e.toString());
						e.printStackTrace();
					}


				} else if(ext.equals(".p12")) {
					try {
						certBytes = new byte[is.available()];
						is.read(certBytes);

						certBytes = android.util.Base64.encode(certBytes, android.util.Base64.DEFAULT);
					} catch (IOException e) {
						Log.e(Storage.LOG, e.toString());
						e.printStackTrace();
					}

				} else if(ext.equals(".asc")) {

					try {
						pgpBytes = new byte[is.available()];
						is.read(pgpBytes);

						pgpBytes = android.util.Base64.encode(pgpBytes, android.util.Base64.DEFAULT);

						PGPPublicKey key;

						key = KeyUtility.extractPublicKeyFromBytes(pgpBytes);
						ksr = new KeyUtility.KeyServerResponse(key);
					} catch (IOException e) {
						Log.e(Storage.LOG, e.toString());
						e.printStackTrace();
					} catch (PGPException e) {
						Log.e(Storage.LOG, e.toString());
						e.printStackTrace();
					}

				}
			}
		}

		try {
			AddressBookDisplay abd = new AddressBookDisplay(a, 0L, ksr.getString(PGP.Keys.PGP_DISPLAY_NAME), ksr.getString(PGP.Keys.PGP_EMAIL_ADDRESS), imgBytes, true);
			abd.put(TrustedDestination.Keys.URL, trustedDestinationURL);
			KeyUtility.installNewKey(dh, db, a, ksr, abd);

			ccr = new ClientCertificateResponse(certBytes, trustedDestinationURL, ksr.getLong(PGP.Keys.PGP_KEY_ID), clientPassword);
			CertificateUtility.storeClientCertificate(dh, db, a, ccr, certBytes);
			
			if(encryptedICTD != null)
				encryptedICTD.delete();
			
			for(File keyFile : ictd.listFiles()[0].listFiles()) {
				keyFile.delete();
			}
			
			for(File folder : ictd.listFiles())
				folder.delete();
			
			ictd.delete();
			
			result = true;
		} catch (JSONException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		} catch(NullPointerException e) {
			Log.e(Storage.LOG, e.toString());
			e.printStackTrace();
		}

		return result;
	}

	public static boolean installICTDPackage(Activity a, File ictd) {
		return installICTDPackage(a, ictd, true);
	}

	public final static class AddressBookDisplay extends JSONObject {
		public AddressBookDisplay(Activity c, long id, String displayName, String emailAddress, byte[] photo, boolean isDeletable) {
			if(photo == null)
				photo = Base64.encode(IOUtility.getBytesFromBitmap(((BitmapDrawable) c.getResources().getDrawable(R.drawable.ic_blank_person)).getBitmap(), 10));

			try {
				this.put(AddressBook.Keys.CONTACT_NAME, displayName);
				this.put(AddressBook.Keys.CONTACT_EMAIL, emailAddress);
				this.put(AddressBook.Keys.CONTACT_ID, id);
				this.put(AddressBook.Keys.CONTACT_PHOTO, new String(photo));
				this.put(TrustedDestination.Keys.IS_DELETABLE, isDeletable);
			} catch (JSONException e) {}
		}
	}

}
