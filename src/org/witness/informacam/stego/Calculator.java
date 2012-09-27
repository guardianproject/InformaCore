package org.witness.informacam.stego;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.storage.IOUtility;
import org.witness.informacam.utils.Constants.Stego;
import org.witness.informacam.utils.Constants.Storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

public class Calculator {
	public static final String tmpJpegPath = Storage.FileIO.DUMP_FOLDER + "stegoCover.jpg";
	
	public static int getStegoSpaceOfEntireJpeg(Bitmap bitmap) {
		int stegoSpace = 0;
		ByteBuffer pixelBuffer = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
		bitmap.copyPixelsToBuffer(pixelBuffer);
		
		// split up image into 8x8 blocks:
		int totalCols = Math.min(bitmap.getRowBytes(), bitmap.getHeight());
		int totalRows = Math.max(bitmap.getRowBytes(), bitmap.getHeight());
		int totalBlocks = totalRows/8;
		
		Log.d(Stego.LOG, "all width: " + bitmap.getRowBytes() + " all height:  " + bitmap.getHeight());
		Log.d(Stego.LOG, "num blocks: " + totalBlocks);
		
		List<int[][]> _8x8s = new ArrayList<int[][]>();
		int c = 0;
		int r = 0;
		int b = -1;
		
		// for each block...
		
		
		/*
		 * for(int rows = 0; rows<totalRows; rows++) {
				Log.d(Stego.LOG, "mod: " + rows % 8);
				if(rows % 8 == 0)
					r = 0;
				
				for(int cols = 0; cols<totalCols; cols++) {
					// row r, col col...
					int[][] _8x8 = new int[8][8];
					
					if(cols % 8 == 0) {
						// make a new matrix if it doesn't exist?
						c = 0;
						try {
							_8x8 = _8x8s.get(blocks);
						} catch(IndexOutOfBoundsException e) {
							Log.d(Stego.LOG, "NEW MATRIX!");
							_8x8s.add(_8x8);
						}
					}
					
					_8x8[r][c] = b.getPixel(rows, cols);
					Log.d(Stego.LOG, "block " + blocks + " is writing pixel [" + r + "," + c + "] is at (" + rows + "," + cols + ")");
					c++;
				}
				
				r++;
			}
		 */
		
		
		// for each block
		
			// get DCT coefficients
		
			// quantize with jpeg quanitzation table
		
			// count how many bits are not 0, 1, or -1; this is stegoSpace
		/*
		try {
			File tmpJpeg = new File(tmpJpegPath);
			
			// compress bitmap to temp jpeg
			FileOutputStream fos = new FileOutputStream(tmpJpeg);
			b.compress(CompressFormat.JPEG, 100, fos);
			fos.flush();
			fos.close();
			stegoSpace = getStegoSpaceOfEntireJpeg(tmpJpeg);
			tmpJpeg.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/

		
		
		// for each block
		
			// get DCT coefficients
		
			// quantize with jpeg quanitzation table
		
			// count how many bits are not 0, 1, or -1; this is stegoSpace
		return stegoSpace;
	}
	
	public static int getStegoSpaceOfEntireJpeg(File jpeg) {
		int stegoSpace = 0;
		
		
		Bitmap b = BitmapFactory.decodeFile(jpeg.getAbsolutePath());
		
		
		
		return stegoSpace;
	}
	
	public static int getStegoSpaceOfEntireJpeg(info.guardianproject.iocipher.File jpeg) {
		File tmpJpeg = IOCipherService.getInstance().cipherFileToJavaFile(jpeg.getAbsolutePath(), tmpJpegPath);
		
		int stegoSpace = getStegoSpaceOfEntireJpeg(tmpJpeg);
		
		tmpJpeg.delete();
		
		return stegoSpace;
	}
}
