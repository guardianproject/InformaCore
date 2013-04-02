package org.witness.informacam.informa.embed;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.witness.informacam.InformaCam;

public class VideoConstructor {
	static String[] libraryAssets = {"ffmpeg"};
	static java.io.File fileBinDir;
	
	InformaCam informaCam;
	
	public VideoConstructor() throws FileNotFoundException, IOException {
		informaCam = InformaCam.getInstance();
		
		fileBinDir = informaCam.a.getDir("bin",0);

		/*
		if (!new java.io.File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}

		videoConstructor = this;
		*/
	}

}
