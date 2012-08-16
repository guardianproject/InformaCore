package org.witness.informacam.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import org.witness.informacam.R;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.ImageRegion.Subject;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AnnotationActivity extends Activity implements OnClickListener {
	EditText subjectNameHolder;
	ImageView imageRegionThumb;
	Button informaSubmit;
	
	HashMap<String, String> mProps;
	ArrayList<Options> informaOptions;
	ListView otherInformaOptionsHolder;
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.annotationactivity);
		
		mProps = (HashMap<String, String>) getIntent().getSerializableExtra(App.ImageEditor.Keys.PROPERTIES);
		try {
			parseChecklist();
		} catch (IOException e) {
			Log.e(App.LOG, "options parse error " + e);
		}
		
		subjectNameHolder = (EditText) findViewById(R.id.subjectNameHolder);
		if(mProps.get(Subject.PSEUDONYM).compareTo("") != 0)
			subjectNameHolder.setText(mProps.get(Subject.PSEUDONYM));
		
		if(getIntent().hasExtra(ImageRegion.THUMBNAIL)) {
			byte[] ba = getIntent().getByteArrayExtra(ImageRegion.THUMBNAIL);
			imageRegionThumb = (ImageView) findViewById(R.id.imageRegionThumb);
			imageRegionThumb.setImageBitmap(IOUtility.getBitmapFromBytes(ba, false));
		}
		
		informaSubmit = (Button) findViewById(R.id.informaSubmit);
		informaSubmit.setOnClickListener(this);
		
		otherInformaOptionsHolder = (ListView) findViewById(R.id.otherInformaOptionsHolder);
		otherInformaOptionsHolder.setAdapter(new Adapter());
	}
		
	public void parseChecklist() throws IOException {
		informaOptions = new ArrayList<Options>();
		BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("informa.checklist")));
		String line;
		
		while((line = br.readLine()) != null) {
			String id = getKey(line, "id");
			String value;
			
			// cycle through mProps to find matching id.  if it's set, this is the value
			if(mProps.containsKey(id))
				value = mProps.get(id);
			else
				value = getKey(line, "defaultValue");
			
			informaOptions.add(new Options(getKey(line, "type"), value, getKey(line, "text"), id));
			
		}
		
		br.close();
	}
	
	private String getKey(String s, String key) {
		if(s.indexOf(key) != -1) {
			String keyTail = s.substring(s.indexOf(key + "="));
			String[] pair = keyTail.substring(0, keyTail.indexOf(";")).split("=");
			return pair[1];
		} else {
			return null;
		}
	}
	
	@Override
	public void onClick(View v) {
		if(v == informaSubmit) {
			if(subjectNameHolder.getText().toString().compareTo("") != 0) {
				mProps.put(Subject.PSEUDONYM, subjectNameHolder.getText().toString());
				
				for(Options opt : informaOptions)
					mProps.put(opt.getId(), opt.getValueAsString());
				
				getIntent().putExtra(ImageRegion.TAGGER_RETURN, mProps);
				getIntent().putExtra(ImageRegion.INDEX, getIntent().getIntExtra(ImageRegion.INDEX, 0));
				setResult(Activity.RESULT_OK,getIntent());
				
				finish();
				
			} else
				Toast.makeText(this, getResources().getString(R.string.error_annotation_empty), Toast.LENGTH_LONG).show();
		}
	}
	
	public class Options {
		String text; 
		private String id;
		Object value;
		
		public Options(String type, Object defaultValue, String text, String id) {
			this.text = text;
			this.id = id;
			
			if(type.equals("boolean"))
				value = new Boolean((String) defaultValue);
		}
		
		public Object getValue() {
			return value;
		}
		
		@SuppressWarnings("unused")
		public String getValueAsString() {
			return value.toString();
		}
		
		@SuppressWarnings("unused")
		public String getId() {
			return id;
		}
		
		public void setValue(Object value) {
			if(value.getClass().equals(this.value.getClass()))
				this.value = value;
		}
	}
	
	public class Adapter extends BaseAdapter {
		LayoutInflater li;
		
		public Adapter() {
			li = LayoutInflater.from(AnnotationActivity.this);
		}
		
		@Override
		public int getCount() {
			return informaOptions.size();
		}

		@Override
		public Object getItem(int position) {
			return informaOptions.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			Options opt = informaOptions.get(position);
			if(opt.getValue().getClass().equals(Boolean.class)) {
				convertView = li.inflate(R.layout.informa_options_checkbox, null);
				TextView optionText = (TextView) convertView.findViewById(R.id.optionText);
				CheckBox optionSelection = (CheckBox) convertView.findViewById(R.id.optionSelection);
				
				optionText.setText(opt.text);
				optionSelection.setChecked((Boolean) informaOptions.get(position).getValue());
				optionSelection.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton button, boolean value) {
						informaOptions.get(position).setValue(value);
					}
					
				});
			}
			return convertView;
		}
	}

}
