package org.witness.informacam.models;

import info.guardianproject.odkparser.FormWrapper;
import info.guardianproject.odkparser.utils.QD;

import java.io.FileNotFoundException;
import java.io.OutputStream;

import org.witness.informacam.models.Model;

import android.app.Activity;
import android.util.Log;
import android.view.View;

public class IForm extends Model {
	public String title = null;
	public String namespace = null;
	public String path = null;
	
	FormWrapper fw = null;
	Activity a = null;
	
	public boolean associate(Activity a, View answerHolder, String questionId) {
		return associate(a, null, answerHolder, questionId);
	}
	
	public boolean associate(Activity a, String initialValue, View answerHolder, String questionId) {
		if(this.a == null) {
			this.a = a;
		}
		
		if(fw == null) {
			try {
				fw = new FormWrapper(new info.guardianproject.iocipher.FileInputStream(path));
			} catch(FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
				return false;
			}
		}
		
		QD questionDef = fw.questions.get(fw.questions.indexOf(getQuestionDefByTitleId(questionId)));
		if(questionDef != null) {
			questionDef.pin(initialValue, answerHolder);
			return true;
		}
		
		return false;
	}
	
	public void answer(String questionId) {
		QD questionDef = fw.questions.get(fw.questions.indexOf(getQuestionDefByTitleId(questionId)));
		if(questionDef != null) {
			questionDef.answer();
		}
		
		
	}
	
	public OutputStream save(OutputStream os) {
		for(QD questionDef : fw.questions) {
			questionDef.commit(fw);
		}
		
		return fw.processFormAsXML(os);
	}

	public QD getQuestionDefByTitleId(String questionId) {
		for(QD qd : fw.questions) {
			Log.d(LOG, "QUESTION DEF ID: " + qd.id);
			if(qd.id.equals(questionId)) {
				return qd;
			}
		}
		
		return null;
	}
}