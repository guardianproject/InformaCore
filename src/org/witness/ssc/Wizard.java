package org.witness.ssc;

import info.guardianproject.database.sqlcipher.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.Apg;
import org.witness.mods.InformaButton;
import org.witness.mods.InformaTextView;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.utils.Selections;
import org.witness.ssc.utils.SelectionsAdapter;
import org.witness.ssc.R;

import com.actionbarsherlock.app.SherlockActivity;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class Wizard extends SherlockActivity implements OnClickListener {
	int current;
		
	LinearLayout frame_content, navigation_holder;
	TextView frame_title, progress;
	InformaButton wizard_next, wizard_back, wizard_done;
	
	private SharedPreferences preferences;
	private SharedPreferences.Editor _ed;
	
	WizardForm wizardForm;
	
	Apg apg;
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);
		
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		_ed = preferences.edit();
		
		current = getIntent().getIntExtra("current", 0);
		wizardForm = new WizardForm(this);
		
		frame_title = (TextView) findViewById(R.id.wizard_frame_title);
		
		frame_content = (LinearLayout) findViewById(R.id.wizard_frame_content);
		navigation_holder = (LinearLayout) findViewById(R.id.wizard_navigation_holder);
		
		wizard_done = (InformaButton) findViewById(R.id.wizard_done);
		wizard_back = (InformaButton) findViewById(R.id.wizard_back);
		wizard_next = (InformaButton) findViewById(R.id.wizard_next);
		
		if(current < wizardForm.frames.length() - 1)
			wizard_next.setOnClickListener(this);
		else {
			wizard_next.setVisibility(View.GONE);
			wizard_back.setVisibility(View.GONE);
			
			wizard_done.setVisibility(View.VISIBLE);
			wizard_done.setOnClickListener(this);
		}
		
		if(current > 0)
			wizard_back.setOnClickListener(this);
		else {
			setMandatory(wizard_back);
		}
		
		progress = (TextView) findViewById(R.id.wizard_progress);
		progress.setText((current + 1) + "/" + wizardForm.frames.length());
		
		try {
			initFrame();
		} catch(JSONException e) {
			Log.e(InformaConstants.TAG, e.toString());
		}
	}
	
	public void setMandatory(View v) {
		((Button) v).setAlpha(0.3f);
		((Button) v).setClickable(false);
	}
	
	public void enableAction(View v) {
		((InformaButton) v).setAlpha(1.0f);
		((InformaButton) v).setClickable(true);
	}
	
	public void disableAction(View v) {
		((InformaButton) v).setAlpha(0.5f);
		((InformaButton) v).setClickable(false);
	}
	
	public void initFrame() throws JSONException {
		wizardForm.setFrame(current);
		frame_title.setText(wizardForm.getTitle());

		ArrayList<View> views = wizardForm.getContent();
		for(View v : views)
			frame_content.addView(v);
	}
	
	@SuppressWarnings("unused")
	private void getUserPGP() {
		apg = Apg.getInstance();
		if(!apg.isAvailable(getApplicationContext()))
			ObscuraConstants.makeToast(this, getResources().getString(R.string.wizard_error_no_apg));
		else {
			apg.selectSecretKey(this);
		}
	}
	
	@SuppressWarnings("unused")
	private void getTrustedDestinations() {
		apg = Apg.getInstance();
		if(!apg.isAvailable(getApplicationContext()))
			ObscuraConstants.makeToast(this, getResources().getString(R.string.wizard_error_no_apg));
		else
			apg.selectEncryptionKeys(this, null);
	}
	
	private void setTrustedDestinations() {
		SQLiteDatabase.loadLibs(this);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
		
		dh.setTable(db, InformaConstants.Keys.Tables.TRUSTED_DESTINATIONS);
		for(long key : apg.getEncryptionKeys()) {
			String userId = apg.getPublicUserId(this, key);
			String email_ = userId.substring(userId.indexOf("<") + 1);
			String email = email_.substring(0, email_.indexOf(">"));
			String displayName = userId.substring(0, userId.indexOf("<"));
			
			if(userId.indexOf("(") != -1)
				displayName = userId.substring(0, userId.indexOf("("));
			
			ContentValues cv = new ContentValues();
			cv.put(InformaConstants.Keys.TrustedDestinations.KEYRING_ID, key);
			cv.put(InformaConstants.Keys.TrustedDestinations.EMAIL, email);
			cv.put(InformaConstants.Keys.TrustedDestinations.DISPLAY_NAME, displayName);
			
			db.insert(dh.getTable(), null, cv);
		}
		enableAction(wizard_next);
		db.close();
	}
	
	private void setUserPGP() {
		
		SQLiteDatabase.loadLibs(this);
		
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
		
		dh.setTable(db, InformaConstants.Keys.Tables.SETUP);
		
		long localTimestamp = System.currentTimeMillis();
		
		ContentValues cv = new ContentValues();
		cv.put(InformaConstants.Keys.Owner.SIG_KEY_ID, apg.getSignatureKeyId());
		cv.put(InformaConstants.Keys.Owner.DEFAULT_SECURITY_LEVEL, InformaConstants.SecurityLevels.UNENCRYPTED_NOT_SHARABLE);
		cv.put(InformaConstants.Keys.Owner.OWNERSHIP_TYPE, InformaConstants.Owner.INDIVIDUAL);
		cv.put(InformaConstants.Keys.Device.LOCAL_TIMESTAMP, localTimestamp);
		cv.put(InformaConstants.Keys.Device.PUBLIC_TIMESTAMP, getPublicTimestamp(localTimestamp));
				
		long insert = db.insert(dh.getTable(), null, cv);
		if(insert != 0)
			enableAction(wizard_next);
		
		db.close();
	}
	
	private long getPublicTimestamp(long ts) {
		//TODO public timestamp?
		return ts;
	}
	
	@SuppressWarnings("unused")
	private void saveDBPW(String pw) {
		_ed.putString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, pw).commit();
	}
	
	@SuppressWarnings("unused")
	private void setDBPWCache(ArrayList<Selections> cacheSelection) {
		for(Selections s : cacheSelection) {
			if(s.getSelected())
				_ed.putString(InformaConstants.Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, String.valueOf(cacheSelection.indexOf(s) + 200)).commit();
		}
	}
	
	@SuppressWarnings("unused")
	private void setDefaultImageHandling(ArrayList<Selections> imageHandlingSelection) {
		for(Selections s : imageHandlingSelection) {
			if(s.getSelected())
				_ed.putString(InformaConstants.Keys.Settings.DEFAULT_IMAGE_HANDLING, String.valueOf(imageHandlingSelection.indexOf(s) + 300)).commit();
		}
	}
	
	@SuppressWarnings("unused")
	private String[] getDefaultImageHandlingOptions() {
		return getResources().getStringArray(R.array.default_image_handling);
	}
	
	@SuppressWarnings("unused")
	private String[] getDBPWCacheValues() {
		return getResources().getStringArray(R.array.password_cache);
	}
	
	private class WizardForm extends JSONObject {
		Context _c;
		JSONArray frames, order;
		JSONObject currentFrame;
		ArrayList<Callback> callbacks;
		
		public final static String frameKey = "frameKey";
		public final static String frameTitle = "frameTitle";
		public final static String frameContent = "frameContent";
		public final static String frameOrder = "frameOrder";
		public final static String allFrames = "frames";
		
		public WizardForm(Context c) {
			_c = c;
			frames = new JSONArray();
			order = new JSONArray();
			callbacks = new ArrayList<Callback>();
			
			// get the list of files within assets/wizard
			try {
				String[] allFiles = _c.getAssets().list("wizard");
				for(String f : allFiles) {
					// get the file
					BufferedReader br = new BufferedReader(new InputStreamReader(_c.getAssets().open("wizard/" + f)));
					String line;
					StringBuilder sb = new StringBuilder();
					while((line = br.readLine()) != null)
						sb.append(line).append('\n');
					
					// if the file is not "order.json"
					if(f.compareTo("order.wizard") == 0) {
						for(String s : sb.toString().split(",")) {
							order.put(s);
						}
					} else {
						JSONObject frame = new JSONObject();
						frame.put(frameKey, f);
						frame.put(frameTitle, parseAsTitle(f));
						frame.put(frameContent, sb.toString());
						frames.put(frame);	
					}
					
					br.close();
				}
				this.put(frameOrder, order);
				this.put(allFrames, frames);
			} catch (IOException e) {
				Log.e(InformaConstants.TAG, e.toString());
			} catch (JSONException e) {
				Log.e(InformaConstants.TAG, e.toString());
			}			
		}
		
		private String parseAsTitle(String rawTitle) {
			String[] words = rawTitle.split("_");
			StringBuffer sb = new StringBuffer();
			for(String word : words) {
				sb.append(word.toUpperCase() + " ");
			}
			
			return sb.toString().substring(0, sb.length() - 1);
		}
		
		public void setFrame(int which) throws JSONException {
			for(int f=0; f<frames.length(); f++) {
				JSONObject frame = frames.getJSONObject(f);
				if(frame.getString(frameKey).compareTo(order.getString(which)) == 0)
					currentFrame = frame;
			}
		}
		
		public ArrayList<Callback> getCallbacks() {
			return callbacks;
		}
		
		private String findKey(String content, String key) {
			if(content.indexOf(key) != -1) {
				String keyTail = content.substring(content.indexOf(key + "="));
				String[] pair = keyTail.substring(0, keyTail.indexOf(";")).split("=");
				return pair[1];
			} else {
				return null;
			}
		}
		
		private String[] parseArguments(String args) {
			String[] a = null;
			if(args != null) {
				a = args.split(",");
			}
			return a;
		}
		
		public ArrayList<View> getContent() throws JSONException {
			ArrayList<View> views = new ArrayList<View>();
			String content = currentFrame.getString(frameContent);
			
			for(final String s : content.split("\n")) {
				if(s.contains("{$")) {
					final String type = findKey(s, "type");
					final String callback = findKey(s, "callback");
					final boolean isMandatory = Boolean.parseBoolean(findKey(s, "mandatory"));
					final String attachTo = findKey(s, "attachTo");
					
					if(isMandatory)
						Wizard.this.setMandatory(wizard_next);
					
					if(type.compareTo("button") == 0) {
						InformaButton button = new InformaButton(_c);
						button.setText(findKey(s, "text"));
						
						String[] args = parseArguments(findKey(s, "args"));
						final Callback buttonCall = new Callback(callback, args); 
						
						button.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								try {
									buttonCall.doCallback();
								} catch (IllegalAccessException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (NoSuchMethodException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (InvocationTargetException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								}
							}
							
						});
						views.add(button);
						
					} else if(type.compareTo("input") == 0) {
						EditText edittext = new EditText(_c);
						
						edittext.addOnLayoutChangeListener(new TextView.OnLayoutChangeListener() {

							@Override
							public void onLayoutChange(View v, int left,
									int top, int right, int bottom,
									int oldLeft, int oldTop, int oldRight,
									int oldBottom) {
								
							}
							
						});
						views.add(edittext);
					} else if(type.compareTo("password") == 0) {
						EditText edittext = new EditText(_c);
						edittext.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
						edittext.setTransformationMethod(new PasswordTransformationMethod());
						
						edittext.addTextChangedListener(new TextWatcher() {

							@Override
							public void afterTextChanged(Editable e) {
								String pw = e.toString();
								if(pw.length() == 0) {} 
								else if(isValidatedPassword(pw)){
									enableAction(wizard_next);
									if(callback != null) {
										if(attachTo == null)
											callbacks.add(new Callback(callback, new String[] {pw}));
										
									}
								} else if(!isValidatedPassword(pw)) {
									disableAction(wizard_next);
								}		
							}

							@Override
							public void beforeTextChanged(CharSequence s,
									int start, int count, int after) {}

							@Override
							public void onTextChanged(CharSequence s,
									int start, int before, int count) {}
							
						});
						
						views.add(edittext);
					} else if(type.compareTo("select_one") == 0 || type.compareTo("select_multi") == 0) {
						
						ArrayList<Selections> selections = new ArrayList<Selections>();
						ListView lv = new ListView(_c);
						
						for(String option : findKey(s, "values").split(",")) {
							Log.d(InformaConstants.TAG, "this option: " + option);
							if(Character.toString(option.charAt(0)).compareTo("#") == 0) {
								// populate from callback
								Callback populate = new Callback(option.substring(1), null);
								
								try {
									for(String res : (String[]) populate.doCallback())
										selections.add(new Selections(res, false));
									
								} catch (IllegalAccessException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (NoSuchMethodException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (InvocationTargetException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								}
							} else 
								selections.add(new Selections(option, false));
						}
						
						callbacks.add(new Callback(callback, new Object[] {selections}));
						lv.setAdapter(new SelectionsAdapter(_c, selections, type));
						views.add(lv);
					}
				} else {
					InformaTextView tv = new InformaTextView(_c);
					tv.setText(s);
					views.add(tv);
				}
			}
			
			return views;
		}
		
		public String getTitle() throws JSONException {
			return currentFrame.getString(frameTitle);
		}
	}
	
	public class Callback {
		String _func;
		Object[] _args;
		
		public Callback(String func, Object[] args) {
			_func = func;
			_args = args;
		}
		
		public Object doCallback() throws  IllegalAccessException, NoSuchMethodException, InvocationTargetException {
			Method method;
			if(_args != null) {
				Class<?>[] paramTypes = new Class[_args.length];
				
				for(int p=0; p<paramTypes.length; p++)
					paramTypes[p] = _args[p].getClass();
				
				method = Wizard.this.getClass().getDeclaredMethod(_func, paramTypes);
			} else
				method = Wizard.this.getClass().getDeclaredMethod(_func, null);
			
			method.setAccessible(true);
			return method.invoke(Wizard.this, _args);
		}
	}
	
	private boolean isValidatedPassword (String password)
	{
		return password.length() >= 6;
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onPause() {
		super.onPause();
	}
	
	@Override
	public void onClick(View v) {
		if(v == wizard_back) {
			if(current > 0) {
				Intent i = new Intent(this,Wizard.class);
				i.putExtra("current", current - 1);
				startActivity(i);
				finish();
			}
		} else if(v == wizard_next) {
			if(current < wizardForm.frames.length() - 1) {
				// do the callbacks...
				for(Callback c: wizardForm.getCallbacks()) {
					try {
						c.doCallback();
					} catch (IllegalAccessException e) {
						Log.d(InformaConstants.TAG, e.toString());
					} catch (NoSuchMethodException e) {
						Log.d(InformaConstants.TAG, e.toString());
					} catch (InvocationTargetException e) {
						Log.d(InformaConstants.TAG, e.toString());
					}
				}
				
				Intent i = new Intent(this,Wizard.class);
				i.putExtra("current", current + 1);
				startActivity(i);
				finish();
			}
		} else if(v == wizard_done) {
			_ed.putBoolean(InformaConstants.Keys.Settings.SETTINGS_VIEWED, true).commit();
			Intent i = new Intent(this, InformaApp.class);
			startActivity(i);
			finish();
		}
		
	}
	
	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		
		if(result == SherlockActivity.RESULT_OK) {
			apg.onActivityResult(this, request, result, data);
			
			switch(request) {
			case Apg.SELECT_SECRET_KEY:
				setUserPGP();
				break;
			case Apg.SELECT_PUBLIC_KEYS:
				setTrustedDestinations();
				break;
			}
		}
			
	}
	
	@Override
	public void onBackPressed() {}

}
