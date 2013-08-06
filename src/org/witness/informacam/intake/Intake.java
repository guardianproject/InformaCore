package org.witness.informacam.intake;

import org.witness.informacam.models.j3m.IDCIMEntry;
import org.witness.informacam.models.j3m.IDCIMDescriptor.IDCIMSerializable;
import org.witness.informacam.utils.BackgroundProcessor;
import org.witness.informacam.utils.Constants.App.Storage;
import org.witness.informacam.utils.Constants.Codes;
import org.witness.informacam.utils.Constants.Logger;
import org.witness.informacam.utils.Constants.Models;

import android.app.IntentService;
import android.content.Intent;

public class Intake extends IntentService {
	BackgroundProcessor queue;
	
	private final static String LOG = "************************** J3M INTAKE **************************";
	
	public Intake() {
		super(Storage.Intake.TAG);		
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		Logger.d(LOG, "onHandleIntent called");

		queue = new BackgroundProcessor();
		queue.setOnBatchComplete(new BatchCompleteJob(queue));
		new Thread(queue).start();

		IDCIMSerializable dcimDescriptor = ((IDCIMSerializable) intent.getSerializableExtra(Codes.Extras.RETURNED_MEDIA));
		long timeOffset = intent.getLongExtra(Codes.Extras.TIME_OFFSET, 0L);
		String cacheFile = intent.getStringExtra(Codes.Extras.INFORMA_CACHE);
		
		String parentId = null;
		if(intent.hasExtra(Codes.Extras.MEDIA_PARENT)) {
			parentId = intent.getStringExtra(Codes.Extras.MEDIA_PARENT);
		}
		
		for(IDCIMEntry entry : dcimDescriptor.dcimList) {
			queue.add(new EntryJob(queue, entry, parentId, cacheFile, timeOffset));
			if(!entry.mediaType.equals(Models.IDCIMEntry.THUMBNAIL)) {
				queue.numProcessing++;
			}
		}
		
		queue.stop();
	}

}
