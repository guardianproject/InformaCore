package org.witness.informacam.utils;

import java.util.ArrayList;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informacam.R;
import org.witness.informacam.app.mods.Selections;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.TrustedDestination;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;

import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;

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
