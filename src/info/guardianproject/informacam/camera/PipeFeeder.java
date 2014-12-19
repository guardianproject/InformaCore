package info.guardianproject.informacam.camera;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class PipeFeeder extends Thread {
	
	InputStream in;
	OutputStream out;
	
	public PipeFeeder(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	//	setDaemon(true);
		this.setPriority(Thread.MAX_PRIORITY);
	}

	@Override
	public void run() {
		
		byte[] buf = new byte[8096];
		int len;

		try {
			int idx = 0;
			while ((len = in.read(buf)) != -1)
			{
				out.write(buf, 0, len);
				idx += buf.length;
				Log.d("video","writing to IOCipher at " + idx);
			}
			
			in.close();
			out.flush();
			out.close();
			
		} catch (IOException e) {
		//	Log.e("Video", "File transfer failed:", e);
		}
	}
}