package org.witness.informacam.app.adapters;

import java.util.ArrayList;

import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.AddressBook;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.AddressBookUtility.AddressBookDisplay;
import org.witness.informacam.utils.Constants.TrustedDestination;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AddressBookAdapter extends BaseAdapter {
	ArrayList<AddressBookDisplay> addresses;
	Activity a;
	
	public interface OnAddressFocusedListener {
		public void onAddressFocusedListener(int which);
	}
	
	public AddressBookAdapter(Activity a, ArrayList<AddressBookDisplay> addresses) {
		this.addresses = addresses;
		this.a = a;
	}
	
	@Override
	public int getCount() {
		return addresses.size();
	}

	@Override
	public Object getItem(int position) {
		return addresses.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		convertView = LayoutInflater.from(a.getApplicationContext()).inflate(R.layout.addressbook_listadapter, null);
		
		try {
			ImageButton contactThumb = (ImageButton) convertView.findViewById(R.id.contact_thumb);
			contactThumb.setImageBitmap(IOUtility.getBitmapFromBytes(addresses.get(position).getString(TrustedDestination.Keys.CONTACT_PHOTO).getBytes(), true));
			
			StringBuilder details = new StringBuilder();
			details.append(addresses.get(position).getString(AddressBook.Keys.CONTACT_NAME) + "\n");
			details.append(addresses.get(position).getString(AddressBook.Keys.CONTACT_EMAIL));
			
			TextView contactDetails = (TextView) convertView.findViewById(R.id.contact_details);
			contactDetails.setText(details.toString());
			
			LinearLayout contactDetailsHolder = (LinearLayout) convertView.findViewById(R.id.contact_details_holder);
			contactDetailsHolder.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					((OnAddressFocusedListener) a).onAddressFocusedListener(position);
					return false;
				}
				
			});
		} catch (JSONException e) {}
		
		return convertView;
	}
}
