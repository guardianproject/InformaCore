package org.witness.informacam;

import android.app.Application;
import org.acra.*;
import org.acra.annotation.*;

@ReportsCrashes(formKey = "dG1TbDFxVUVTLVAtbU9QYVpJQnVxM3c6MQ")
public class InformaCam extends Application {
	public void onCreate() {
		super.onCreate();
		
		ACRA.init(this);
	}

}
