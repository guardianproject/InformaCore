package org.witness.informacam.models.organizations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import org.spongycastle.openpgp.PGPException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.crypto.KeyUtility;
import org.witness.informacam.models.Model;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.ui.popups.UpdateICTDPopup;
import org.witness.informacam.utils.Constants.App.Storage.Type;
import org.witness.informacam.utils.Constants.Logger;

import android.app.Activity;
import android.os.Handler;
import android.view.View;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class IInstalledOrganizations extends Model {
	public List<IOrganization> organizations = new ArrayList<IOrganization>();
	
	public List<IOrganization> listOrganizations() {
		return organizations;
	}
	
	public IOrganization getByName(final String organizationName) {
		Collection<IOrganization> organizations_ = Collections2.filter(organizations, new Predicate<IOrganization>() {
			@Override
			public boolean apply(IOrganization o) {
				return o.organizationName.equals(organizationName);
			}
		});
		
		try {
			return organizations_.iterator().next();
		} catch(NullPointerException e) {
			return null;
		} catch(NoSuchElementException e) {
			return null;
		}
	}
	
	public IOrganization getByFingerprint(final String fingerprint) {
		Collection<IOrganization> organizations_ = Collections2.filter(organizations, new Predicate<IOrganization>() {
			@Override
			public boolean apply(IOrganization o) {
				return o.organizationFingerprint.toLowerCase().equals(fingerprint.toLowerCase());
			}
		});
		
		try {
			return organizations_.iterator().next();
		} catch(NullPointerException e) {
			return null;
		} catch(NoSuchElementException e) {
			return null;
		}
	}
	
	
	public void save() {
		InformaCam.getInstance().saveState(this);
	}

	public void addOrganization(final IOrganization organization, Activity a) {
		final IOrganization possibleDuplicate = getByFingerprint(organization.organizationFingerprint);
		
		if(possibleDuplicate == null) {
			organizations.add(organization);
			save();
		} else {
			StringBuffer sb = new StringBuffer();
			sb.append(a.getString(R.string.an_ictd_for_x_already, organization.organizationName) + "\n");
			sb.append("\n" + a.getString(R.string.old_ictd) + "\n");
			sb.append("\n" + possibleDuplicate.asJson().toString() + "\n");
			sb.append(a.getString(R.string.new_ictd) + "\n");
			sb.append("\n" + organization.asJson().toString() + "\n");
			
			UpdateICTDPopup updateICTDPopup = new UpdateICTDPopup(a, sb.toString()) {
				@Override
				public void onClick(View v) {
					if(v == ok) {
						possibleDuplicate.inflate(organization);
						save();
					}
					
					cancel();
				}
			};
			
			
		}

	}
	
}
