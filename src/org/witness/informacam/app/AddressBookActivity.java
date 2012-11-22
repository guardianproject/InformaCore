package org.witness.informacam.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.MainRouter.OnRoutedListener;
import org.witness.informacam.app.adapters.AddressBookAdapter;
import org.witness.informacam.app.adapters.AddressBookAdapter.OnAddressFocusedListener;
import org.witness.informacam.app.adapters.AddressBookChooserAdapter;
import org.witness.informacam.app.mods.InformaChoosableAlert;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.DatabaseService;
import org.witness.informacam.utils.AddressBookUtility;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.utils.Constants.Storage.Tables;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

public class AddressBookActivity extends Activity implements OnClickListener, OnRoutedListener, OnAddressFocusedListener, OnChoosableChosenListener {

	Handler h;

	ImageButton navigation;
	Button new_contact, select_contact;
	ListView address_list;

	SQLiteDatabase db;
	DatabaseHelper dh;

	ProgressDialog mProgressDialog;

	boolean isSelecting = false;
	ArrayList<AddressBookDisplay> addresses;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(getIntent().getExtras() != null && getIntent().getExtras().containsKey(App.ImageEditor.Keys.CHOOSE_TRUSTED_DESTINATION))
			initLayoutForChooser();
		else
			initLayout();

		if(MainRouter.show(this)) {

			if(getIntent().getData() != null) {
				final Uri ictd_uri = getIntent().getData();
				Log.d(App.LOG, "INIT KEY: " + ictd_uri);

				h.post(new Runnable() {
					private File parseICTD() {
						File ictd = new File(Storage.FileIO.DUMP_FOLDER, Storage.FileIO.ICTD_TEMP);
						try {
							InputStream ictd_ = getContentResolver().openInputStream(getIntent().getData());
							FileOutputStream fos = new FileOutputStream(ictd);
							byte[] buf = new byte[ictd_.available()];
							ictd_.read(buf);

							fos.write(buf);
							fos.flush();

							fos.close();
							ictd_.close();
						} catch(IOException e) {
							return null;
						}

						return ictd;
					}

					@Override
					public void run() {
						boolean installed = AddressBookUtility.installICTDPackage(AddressBookActivity.this, parseICTD());
						mProgressDialog.dismiss();
						if(installed) {
							getAddresses();
						} else {
							Toast.makeText(AddressBookActivity.this, getString(R.string.error_invalid_ictd), Toast.LENGTH_LONG).show();
						}
						parseICTD();
					}
				});
			}
		}
	}

	private void initLayout() {
		setContentView(R.layout.addressbookactivity);

		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);

		new_contact = (Button) findViewById(R.id.new_contact_button);
		new_contact.setOnClickListener(this);

		address_list = (ListView) findViewById(R.id.address_book_list);

		mProgressDialog = new ProgressDialog(this);
	}

	private void initLayoutForChooser() {
		setContentView(R.layout.addressbookactivity_chooser);

		navigation = (ImageButton) findViewById(R.id.navigation_button);
		navigation.setOnClickListener(this);

		select_contact = (Button) findViewById(R.id.select_contact_button);
		select_contact.setOnClickListener(this);

		address_list = (ListView) findViewById(R.id.address_book_list);
		isSelecting = true;
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainactivity_menu, menu);
        menu.removeItem(R.id.menu_refresh);
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem mi) {
    	switch(mi.getItemId()) {
    	case R.id.extras_about:
    		MainRouter.launchAbout(AddressBookActivity.this);
    		return true;
    	case R.id.extras_preferences:
    		MainRouter.launchPreferences(AddressBookActivity.this);
    		return true;
    	case R.id.extras_knowledgebase:
    		MainRouter.launchKnowledgebase(AddressBookActivity.this);
    		return true;
    	case R.id.extras_send_log:
    		MainRouter.launchSendLog(AddressBookActivity.this);
    		return true;
    	case R.id.extras_logout:
    		MainRouter.doLogout(AddressBookActivity.this);
    		finish();
    		return true;
    	case R.id.menu_export_public_key:
    		MainRouter.exportDeviceKey(AddressBookActivity.this);
    		return true;
    	default:
    		return false;
    	}
    }

	private void getAddresses() {
		dh.setTable(db, Tables.Keys.TRUSTED_DESTINATIONS);
		Cursor a = dh.getValue(db, AddressBook.Projections.LIST_DISPLAY, null, null);
		if(a != null && a.getCount() > 0) {
			addresses = new ArrayList<AddressBookDisplay>();
			a.moveToFirst();
			while(!a.isAfterLast()) {
				AddressBookDisplay abd = new AddressBookDisplay(
						AddressBookActivity.this, 0L, 
						a.getString(a.getColumnIndex(TrustedDestination.Keys.DISPLAY_NAME)),
						a.getString(a.getColumnIndex(TrustedDestination.Keys.EMAIL)),
						a.getBlob(a.getColumnIndex(TrustedDestination.Keys.CONTACT_PHOTO)),
						a.getInt(a.getColumnIndex(TrustedDestination.Keys.IS_DELETABLE)) == 0 ? false:true
						);

				try {
					abd.remove(AddressBook.Keys.CONTACT_ID);
					abd.put(TrustedDestination.Keys.KEYRING_ID, a.getLong(a.getColumnIndex(TrustedDestination.Keys.KEYRING_ID)));
					abd.put(BaseColumns._ID, a.getLong(a.getColumnIndex(BaseColumns._ID)));
				} catch(JSONException e) {}

				addresses.add(abd);
				a.moveToNext();
			}

			a.close();

			if(isSelecting)
				address_list.setAdapter(new AddressBookChooserAdapter(AddressBookActivity.this, addresses));
			else
				address_list.setAdapter(new AddressBookAdapter(AddressBookActivity.this, addresses));
		} else {
			ErrorHandler.show(this, getString(R.string.error_no_trusted_destinations));
		}
	}

	@Override
	public void onClick(View v) {
		if(v == navigation)
			finish();
		else if(v == new_contact) {
			AddressBookImporterDialog abid = new AddressBookImporterDialog(AddressBookActivity.this);
			mProgressDialog = ProgressDialog.show(AddressBookActivity.this, "", getString(R.string.please_wait), false, false);
			abid.show();
		} else if(v == select_contact && addresses != null) {
			List<Long> encryptList = new ArrayList<Long>();
			for(AddressBookDisplay adr : addresses) {

				try {
					if(adr.has(AddressBook.Keys.CONTACT_SELECTED) && adr.getBoolean(AddressBook.Keys.CONTACT_SELECTED))
						encryptList.add(adr.getLong(TrustedDestination.Keys.KEYRING_ID));

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			long[] encryptListArray = new long[encryptList.size()];
			for(int l=0; l<encryptList.size(); l++)
				encryptListArray[l] = encryptList.get(l);

			getIntent().putExtra(Informa.Keys.Intent.ENCRYPT_LIST, encryptListArray);
			setResult(Activity.RESULT_OK, getIntent());
			finish();
		}

	}

	@Override
	public void onRouted() {
		h = new Handler();

		dh = DatabaseService.getInstance().getHelper();
		db = DatabaseService.getInstance().getDb();

		h.post(new Runnable() {
			@Override
			public void run() {
				getAddresses();
			}
		});

	}

	@Override
	public void onAddressFocusedListener(int which) {
		InformaChoosableAlert alert = new InformaChoosableAlert(AddressBookActivity.this, getResources().getStringArray(R.array.address_book_actions), addresses.get(which));
		try {
			alert.setTitle(addresses.get(which).getString(AddressBook.Keys.CONTACT_NAME));
		} catch (JSONException e) {}
		alert.show();
	}

	@Override
	public void onChoice(int which, final Object obj) {
		if(obj instanceof AddressBookDisplay) {
			AddressBookDisplay abd = (AddressBookDisplay) obj;
			switch(which) {
			case AddressBook.Actions.DELETE_CONTACT:
				try {
					if(abd.getBoolean(TrustedDestination.Keys.IS_DELETABLE)) {
						AddressBookUtility.deleteContact(abd.getLong(BaseColumns._ID), abd.getString(AddressBook.Keys.CONTACT_EMAIL));
						getAddresses();
					} else {
						Toast.makeText(this, getString(R.string.error_cannot_delete_contact), Toast.LENGTH_LONG).show();
					}
				} catch (JSONException e) {}
				break;
			case AddressBook.Actions.REFRESH_KEY:
				break;
			case AddressBook.Actions.VIEW_DETAILS:
				break;

			}
		} else if(obj instanceof File) {

			h.post(new Runnable() {
				@Override
				public void run() {
					boolean installed = AddressBookUtility.installICTDPackage(AddressBookActivity.this, (File) obj);
					mProgressDialog.dismiss();
					if(installed) {
						getAddresses();
					} else {
						Toast.makeText(AddressBookActivity.this, getString(R.string.error_invalid_ictd), Toast.LENGTH_LONG).show();
					}
				}
			});

		}
	}

	@Override
	public void onCancel() {
		Log.d(App.LOG, "UM CANCEL DIALOG?");
		try {
			mProgressDialog.dismiss();
		} catch(NullPointerException e) {
			
		}
		
	}

}
