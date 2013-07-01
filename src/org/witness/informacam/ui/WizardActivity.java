package org.witness.informacam.ui;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.witness.informacam.InformaCam;
import org.witness.informacam.R;
import org.witness.informacam.ui.screens.WizardStepOne;
import org.witness.informacam.ui.screens.WizardStepThree;
import org.witness.informacam.ui.screens.WizardStepTwo;
import org.witness.informacam.ui.screens.WizardStepZero;
import org.witness.informacam.ui.screens.WizardSubFragmentFinish;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.InformaCamEventListener;
import org.witness.informacam.utils.Constants.WizardListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TabHost;
import android.widget.TextView;

public class WizardActivity extends FragmentActivity implements WizardListener, InformaCamEventListener {
	Intent init;
	private final static String LOG = App.LOG;


	TabHost tabHost;
	ViewPager viewPager;
	TabPager pager;

	List<Fragment> fragments = new Vector<Fragment>();
	public List<Fragment> subFragments = null;

	boolean mainWizardCanContinue = false;
	boolean subWizardCanContinue = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		((InformaCam)getApplication()).setEventListener(this);

		setContentView(R.layout.activity_wizard);
		
		if(getIntent().hasExtra(Codes.Extras.SET_LOCALES)) {
			Bundle locales = new Bundle();
			locales.putSerializable(Codes.Extras.SET_LOCALES, getIntent().getSerializableExtra(Codes.Extras.SET_LOCALES));
			locales.putString(Codes.Extras.LOCALE_PREF_KEY, getIntent().getStringExtra(Codes.Extras.LOCALE_PREF_KEY));
			
			fragments.add(Fragment.instantiate(this, WizardStepZero.class.getName(), locales));
		}
		
		fragments.add(Fragment.instantiate(this, WizardStepOne.class.getName()));
		fragments.add(Fragment.instantiate(this, WizardStepTwo.class.getName()));
		fragments.add(Fragment.instantiate(this, WizardStepThree.class.getName()));

		viewPager = (ViewPager) findViewById(R.id.view_pager_root);

		if(getIntent().hasExtra(Codes.Extras.WIZARD_SUPPLEMENT)) {
			subFragments = new Vector<Fragment>();
			for(String f : getIntent().getStringArrayListExtra(Codes.Extras.WIZARD_SUPPLEMENT)) {
				subFragments.add(Fragment.instantiate(this, f));
			}
			subFragments.add(Fragment.instantiate(this, WizardSubFragmentFinish.class.getName()));
		}
		
		

		initLayout();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public void initLayout() {
		pager = new TabPager(getSupportFragmentManager());

		tabHost = (TabHost) findViewById(android.R.id.tabhost);
		tabHost.setup();

		tabHost.setOnTabChangedListener(pager);
		viewPager.setAdapter(pager);
		viewPager.setOnPageChangeListener(pager);
		viewPager.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}

		});

		TabHost.TabSpec tabSpec = null;

		@SuppressWarnings("unused")
		View indicator = null;

		int i = 1;

		for(Fragment f : fragments) {
			tabSpec = tabHost.newTabSpec(f.getClass().getSimpleName())
					.setIndicator(indicator = new Indicator(String.valueOf(i), this).tab)
					.setContent(new TabHost.TabContentFactory() {

						@Override
						public View createTabContent(String tag) {
							View view = new View(WizardActivity.this);
							view.setMinimumHeight(0);
							view.setMinimumWidth(0);
							return view;
						}
					});
			tabHost.addTab(tabSpec);
			i++;
		}
		
		if(getIntent().hasExtra(Codes.Extras.CHANGE_LOCALE)) {
			getIntent().removeExtra(Codes.Extras.CHANGE_LOCALE);
			viewPager.setCurrentItem(1);
		}


	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static class Indicator {
		View tab;

		Indicator(String label_string, Context c) {
			tab = LayoutInflater.from(c).inflate(R.layout.tab_layout_wizard, null);
			TextView label = (TextView) tab.findViewById(R.id.tab_label);
			label.setText(label_string);
		}
	}

	class TabPager extends FragmentStatePagerAdapter implements TabHost.OnTabChangeListener, OnPageChangeListener {

		public TabPager(FragmentManager fm) {
			super(fm);
		}

		@Override
		public void onTabChanged(String tabId) {}

		@Override
		public void onPageScrollStateChanged(int state) {}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {}

		@Override
		public void onPageSelected(int page) {
			tabHost.setCurrentTab(page);
			Log.d(LOG, "setting current page as " + page);
		}

		@Override
		public Fragment getItem(int which) {
			return fragments.get(which);
		}


		@Override
		public int getCount() {
			return fragments.size();
		}

	}

	@Override
	public FragmentManager returnFragmentManager() {
		return getSupportFragmentManager();
	}

	@Override
	public void wizardCompleted() {
		// save everything and finish
		
		InformaCam informaCam = (InformaCam)getApplication();
		
		informaCam.user.hasCompletedWizard = true;
		informaCam.user.lastLogIn = System.currentTimeMillis();
		informaCam.user.isLoggedIn = true;
		
		informaCam.saveState(informaCam.user);
		informaCam.saveState(informaCam.languageMap);

		if(!informaCam.user.isInOfflineMode) {
			/*
			IPendingConnections pendingConnections = (IPendingConnections) informaCam.getModel(new IPendingConnections());
			
			for(IConnection connection : pendingConnections.queue) {
				connection.setParam(IUser.PGP_KEY_FINGERPRINT, informaCam.user.pgpKeyFingerprint);
				connection.setParam(IUser.ALIAS, informaCam.user.alias);
				connection.setParam(IUser.EMAIL, informaCam.user.email);
				connection.setData(IUser.PUBLIC_CREDENTIALS);
				connection.data.byteRange = new int[] {0, informaCam.ioService.getBytes(IUser.PUBLIC_CREDENTIALS, Type.IOCIPHER).length};
				connection.isHeld = false;

				connection.save();

			}
			*/
		}
		
		Iterator<String> it = getIntent().getExtras().keySet().iterator();
		while(it.hasNext()) {
			getIntent().removeExtra(it.next());
		}
		
		setResult(Activity.RESULT_OK);
		finish();
	}
	
	@Override
	public void onConfigurationChanged(Configuration configuration) {
		setResult(Activity.RESULT_FIRST_USER, new Intent().putExtra(Codes.Extras.CHANGE_LOCALE, true));
		finish();
	}

	@Override
	public void onUpdate(Message message) {
		((InformaCamEventListener) fragments.get(3)).onUpdate(message);

	}

	public void autoAdvance() {
		Log.d(LOG, "advancing to " + (viewPager.getCurrentItem() + 1));
		viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
		
	}

	@Override
	public void onSubFragmentCompleted() {}

	@Override
	public void onSubFragmentInitialized() {}
}
