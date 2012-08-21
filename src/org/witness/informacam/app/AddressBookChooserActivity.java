package org.witness.informacam.app;

import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.mods.InformaButton;
import org.witness.informacam.app.mods.InformaEditText;
import org.witness.informacam.app.mods.SelectionDisplay;
import org.witness.informacam.app.mods.Selections;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.crypto.KeyUtility.KeyFoundListener;
import org.witness.informacam.crypto.KeyUtility.KeyServerResponse;
import org.witness.informacam.utils.AddressBookUtility;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.AddressBookUtility.AddressBookListener;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto.PGP;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AddressBookChooserActivity extends Activity implements OnClickListener, TextWatcher, AddressBookListener, KeyFoundListener {
	InformaButton input_contact, pick_contact, import_contact, done;
	InformaEditText input_contact_text;
	LinearLayout contact_info;
	TextView choose_instructions;
		
	String queryEmail;
	long queryId;
	
	KeyServerResponse ksr;
	AddressBookDisplay abd;
	
	Handler h;
	
	int stage = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initLayout();
	}
	
	private void initLayout() {
		setContentView(R.layout.addressbookchooseractivity);
		
		choose_instructions = (TextView) findViewById(R.id.choose_instructions);
		
		input_contact = (InformaButton) findViewById(R.id.input_contact);
		input_contact.setOnClickListener(this);
		disableAction(input_contact);
		
		pick_contact = (InformaButton) findViewById(R.id.pick_contact);
		pick_contact.setOnClickListener(this);
		
		import_contact = (InformaButton) findViewById(R.id.import_contact);
		import_contact.setOnClickListener(this);
		
		done = (InformaButton) findViewById(R.id.address_book_done);
		done.setOnClickListener(this);
		disableAction(done);
		
		input_contact_text = (InformaEditText) findViewById(R.id.input_contact_text);
		input_contact_text.addTextChangedListener(this);
		
		contact_info = (LinearLayout) findViewById(R.id.contact_info);
		reloadViews(stage);
	}
	
	private void reloadViews(int stage) {
		contact_info.removeAllViews();
		
		switch(stage) {
		case 0:
			choose_instructions.setText(R.string.address_book_directions);
			input_contact_text.setVisibility(View.VISIBLE);
			input_contact.setVisibility(View.VISIBLE);
			import_contact.setVisibility(View.VISIBLE);
			pick_contact.setVisibility(View.VISIBLE);
			done.setVisibility(View.GONE);
			break;
		case 1:
			choose_instructions.setText(R.string.address_book_multiple_email);
			input_contact_text.setVisibility(View.GONE);
			input_contact.setVisibility(View.GONE);
			import_contact.setVisibility(View.GONE);
			pick_contact.setVisibility(View.GONE);
			done.setVisibility(View.GONE);
			break;
		case 2:
			choose_instructions.setText(R.string.address_book_querying_keyserver_wait);
			input_contact_text.setVisibility(View.GONE);
			input_contact.setVisibility(View.GONE);
			import_contact.setVisibility(View.GONE);
			pick_contact.setVisibility(View.GONE);
			done.setVisibility(View.GONE);
			break;
		case 3:
			choose_instructions.setText(R.string.address_book_keyserver_result);
			input_contact_text.setVisibility(View.GONE);
			input_contact.setVisibility(View.GONE);
			import_contact.setVisibility(View.GONE);
			pick_contact.setVisibility(View.GONE);
			done.setVisibility(View.VISIBLE);
			enableAction(done);
		}
	}
	
	private void enableAction(View v) {
		((InformaButton) v).getBackground().setAlpha(255);
		((InformaButton) v).setClickable(true);
	}
	
	private void disableAction(View v) {
		((InformaButton) v).getBackground().setAlpha(100);
		((InformaButton) v).setClickable(false);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == Activity.RESULT_OK) {
			switch(requestCode) {
			case App.AddressBook.FROM_CONTACT_CHOOSER:
				queryId = Long.parseLong(data.getData().getLastPathSegment());
				AddressBookUtility.getEmailAddressFromLookup(AddressBookChooserActivity.this, queryId);
				break;
			case App.AddressBook.FROM_ASC_IMPORT:
				// TODO:  this.
				break;
			}
		}
	}
	
	@Override
	public void onClick(View v) {
		if(v == input_contact) {
			queryEmail = input_contact_text.getText().toString();
			AddressBookUtility.getLookupFromEmailAddress(AddressBookChooserActivity.this, queryEmail);
		} else if(v == pick_contact) {
			Intent intent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
			startActivityForResult(intent, App.AddressBook.FROM_CONTACT_CHOOSER);
		} else if(v == import_contact) {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			startActivityForResult(intent, App.AddressBook.FROM_ASC_IMPORT);
		} else if(v == done) {
			KeyUtility.installNewKey(AddressBookChooserActivity.this, ksr, abd);
			finish();
		}
		
	}

	@Override
	public void afterTextChanged(Editable e) {
		if(e.length() >= 5 && e.toString().contains("@")) {
			enableAction(input_contact);
		} else
			disableAction(input_contact);
		
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {}

	@Override
	public void receiveNewAddresses(AddressBookDisplay abd) {
		this.abd = abd;
		try {
			stage = 2;
			reloadViews(stage);
			KeyUtility.queryKeyserverByEmail(AddressBookChooserActivity.this, abd.getString(AddressBook.Keys.CONTACT_NAME), abd.getString(AddressBook.Keys.CONTACT_EMAIL));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public void onNoResultFound(String emailAddress) {
		Toast.makeText(this, getString(R.string.error_no_key_found_for_email) + " " + emailAddress, Toast.LENGTH_LONG).show();
		
	}

	@Override
	public void onMultipleChoicesFound(ArrayList<Selections> choices) {
		reloadViews(++stage);
		final SelectionDisplay sd = new SelectionDisplay(this, choices);
		contact_info.addView(sd);
		
		sd.ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				for(int i=0; i<sd.selectionsList.getAdapter().getCount(); i++) {
					Selections s = (Selections) sd.selectionsList.getAdapter().getItem(i);
					if(s.getSelected())
						AddressBookUtility.getEmailAddressFromLookup(AddressBookChooserActivity.this, queryId, i);
					reloadViews(++stage);
				}
				
			}
		});
	}
	
	@Override
	public void onBackPressed() {
		if(stage == 0)
			finish();
		else {
			stage = 0;
			reloadViews(stage);
		}
	}

	@Override
	public void onKeyFound(KeyServerResponse ksr) {
		this.ksr = ksr;
		stage = 3;
		reloadViews(stage);
		TextView confirmation = new TextView(this);
		try {
			confirmation.setText(
				ksr.getString(PGP.Keys.PGP_DISPLAY_NAME) + "\n" +
				getString(R.string.address_book_email) + " " + ksr.getString(PGP.Keys.PGP_EMAIL_ADDRESS) + "\n" +
				getString(R.string.address_book_fingerprint) + "\n" + ksr.getString(PGP.Keys.PGP_FINGERPRINT)
			);
		} catch(JSONException e) {}
		
		contact_info.addView(confirmation);
	}
}
