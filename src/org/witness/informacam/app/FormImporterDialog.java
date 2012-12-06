package org.witness.informacam.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.R;
import org.witness.informacam.app.mods.InformaChoosableAlert.OnChoosableChosenListener;
import org.witness.informacam.utils.Constants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class FormImporterDialog extends AlertDialog {
	Activity a;
	ListView fileList;
	LayoutInflater li;
	View inner;
	String lastF;
	FormImporterDialog fid;
	
	protected FormImporterDialog(Context context) {
		super(context);
	}
	
	protected FormImporterDialog(Activity a) {
		super(a);
		this.a = a;
		
		li = LayoutInflater.from(a);
		inner = li.inflate(R.layout.directoryadapter_listview, null);
		
		setView(inner);
		
		lastF = Environment.getExternalStorageDirectory().getAbsolutePath();
		
		fileList = (ListView) inner.findViewById(R.id.directoryList);
		fileList.setAdapter(new DirListAdapter(lastF));
		
		fid = this;
	}
	
	@Override
	public void onBackPressed() {
		((OnChoosableChosenListener) a).onCancel();
		fid.dismiss();
	}
	
	public class DirListAdapter extends BaseAdapter {
		List<File> files;
		File dir;
		
		public DirListAdapter(String dir_) {
			dir = new File(dir_);
			files = new ArrayList<File>();
			files.add(new File(lastF));
			
			for(File f : dir.listFiles())
				files.add(f);
		}
		
		@Override
		public int getCount() {
			return files.size();
		}

		@Override
		public Object getItem(int position) {
			return files.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			final File file = files.get(position);
			convertView = li.inflate(R.layout.directoryadapter_listitem, null);
			Drawable d = a.getResources().getDrawable(R.drawable.ic_folder_grey);
			
			if(file.isDirectory()) {
				if(!file.getParent().equals("/")) {
					d = a.getResources().getDrawable(R.drawable.ic_folder_green);
					convertView.setOnClickListener(new View.OnClickListener() {
						
						@Override
						public void onClick(View v) {
							lastF = file.getParent();
							fileList.setAdapter(new DirListAdapter(file.getAbsolutePath()));
							
						}
					});
				}
			} else if(file.isFile()){
				d = a.getResources().getDrawable(R.drawable.ic_file_grey);
			
				if(file.getName().lastIndexOf(".") != -1) {
					String lastBit = file.getName().substring(file.getName().lastIndexOf("."));
					if(lastBit.equals(Constants.Media.Type.XML)) { // ".xml"!
						d = a.getResources().getDrawable(R.drawable.ic_file_blue);
						convertView.setOnClickListener(new View.OnClickListener() {
							
							@Override
							public void onClick(View v) {
								fid.dismiss();
								((OnChoosableChosenListener) a).onChoice(position, file);
							}
						});
					}
				}
					
			}
			
			ImageView fThumb = (ImageView) convertView.findViewById(R.id.fThumb);
			fThumb.setImageDrawable(d);
			
			TextView fName = (TextView) convertView.findViewById(R.id.fName);
			if(file.getAbsolutePath().equals(lastF))
				fName.setText("..");
			else
				fName.setText(file.getName());
			return convertView;
		}
		
	}
	
}
