package info.guardianproject.informacam.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import org.witness.informacam.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

public class ImageViewerActivity extends Activity {


	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.image);
		
		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		
		ImageView iv = (ImageView)findViewById(R.id.imageview);
		
		if (intent.hasExtra("vfs"))
		{
			try
			{
				File file = new File(intent.getExtras().getString("vfs"));
				Bitmap bmp = BitmapFactory.decodeStream(new FileInputStream(file));
				iv.setImageBitmap(bmp);
			}
			catch (Exception e)
			{
				Log.d("Image","error showing vfs image",e);
			}
		}
		else
		{
			iv.setImageURI(intent.getData());
		}
		
	}

	protected void onResume() {
		super.onResume();
		
	}

	protected void onDestroy() {
		super.onDestroy();
		
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
      //  MenuInflater inflater = getMenuInflater();
       // inflater.inflate(R.menu.main, menu);
        
        return true;
	}
	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	/**
        switch (item.getItemId()) {

        case R.id.menu_camera:
        	
        	Intent intent = new Intent(this,SecureSelfieActivity.class);
        	intent.putExtra("basepath", "/");
        	startActivityForResult(intent, 1);
        	
        	return true;
        case R.id.menu_video:
        	
        	intent = new Intent(this,VideoRecorderActivity.class);
        	intent.putExtra("basepath", "/");
        	startActivityForResult(intent, 1);
        	
        	return true;	
        }	*/
        
        return false;
    }

	
	
		
	
		
		
}
