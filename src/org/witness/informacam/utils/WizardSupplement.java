package org.witness.informacam.utils;

import org.witness.informacam.utils.Constants.WizardListener;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.support.v4.app.FragmentManager;

@SuppressLint("NewApi")
public class WizardSupplement extends Fragment implements WizardListener {

	@Override
	public FragmentManager returnFragmentManager() {
		return null;
	}

	@Override
	public void wizardCompleted() {}

	@Override
	public void onSubFragmentCompleted() {
		/**
		 * this handler is called right before a user
		 * clicks past the preference fragment.
		 * 
		 * This method is most helpful for setting
		 * app-specific preferences.
		 */
	}

	@Override
	public void onSubFragmentInitialized() {}

}
