package org.witness.informacam.models.forms;

import info.guardianproject.odkparser.FormWrapper;
import info.guardianproject.odkparser.utils.QD;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.javarosa.core.model.QuestionDef;
import org.json.JSONObject;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.Model;
import org.witness.informacam.utils.Constants.App.Storage.Type;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

public class IForm extends Model {
	public String title = null;
	public String namespace = null;
	public String path = null;

	FormWrapper fw = null;
	Activity a = null;
	
	public String[] populateAnswers(byte[] oldAnswers) {
		String[] answers = null;
		try {
			fw = new FormWrapper(new info.guardianproject.iocipher.FileInputStream(path), oldAnswers);
			answers = new String[fw.questions.size()];
			int answer = 0;
			for(QD qd : fw.questions) {
				answers[answer] = qd.initialValue != null ? qd.initialValue : "";
				answer++;
			}
		} catch (FileNotFoundException e) {
			Log.e(LOG, e.toString());
			e.printStackTrace();
		}
		
		return answers;
	}
	
	public void clear() {
		for(QD qd : fw.questions) {
			qd.clear();
		}
	}

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

	@SuppressWarnings("unchecked")
	public List<View> buildUI(Activity a, String pathToOldAnswers, int[] inputLayout, int[] selectOneLayout, int[] selectMultiLayout, int[] audioCaptureLayout) {
		LayoutInflater li = LayoutInflater.from(a);
		
		if(this.a == null) {
			this.a = a;
		}

		if(fw == null) {
			try {
				if(pathToOldAnswers == null) {
					fw = new FormWrapper(new info.guardianproject.iocipher.FileInputStream(path));
				} else {
					byte[] oldAnswers = InformaCam.getInstance().ioService.getBytes(pathToOldAnswers, Type.IOCIPHER);
					fw = new FormWrapper(new info.guardianproject.iocipher.FileInputStream(path), oldAnswers);
				}
			} catch(FileNotFoundException e) {
				Log.e(LOG, e.toString());
				e.printStackTrace();
			}
		}
		
		List<View> views = new Vector<View>();

		int v = 0;
		for(QD questionDef : fw.questions) {
			View view = null;
			switch(questionDef.getQuestionDef().getControlType()) {
			case org.javarosa.core.model.Constants.CONTROL_INPUT:
				view = li.inflate(inputLayout[0], null);
				view.setTag(QD.map(inputLayout));
				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_ONE:
				view = li.inflate(selectOneLayout[0], null);
				view.setTag(QD.map(selectOneLayout));
				break;
			case org.javarosa.core.model.Constants.CONTROL_SELECT_MULTI:
				view = li.inflate(selectMultiLayout[0], null);
				view.setTag(QD.map(selectMultiLayout));
				break;
			case org.javarosa.core.model.Constants.CONTROL_AUDIO_CAPTURE:
				view = li.inflate(audioCaptureLayout[0], null);
				view.setTag(QD.map(audioCaptureLayout));
				break;
			}

			Map<String, Integer> viewMap = (Map<String, Integer>) view.getTag();
			Log.d(LOG, "ok have a map:");
			Iterator<Entry<String, Integer>> vIt = viewMap.entrySet().iterator();
			while(vIt.hasNext()) {
				Entry<String, Integer> entry = vIt.next();
				Log.d(LOG, entry.getKey() + ": " + entry.getValue());
			}

			view = questionDef.buildUI(a, view);
			view.setId(v);
			views.add(view);
			v++;
		}

		return views;
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
	
	public JSONObject save() {
		for(QD questionDef : fw.questions) {
			questionDef.commit(fw);
		}
		
		return fw.processFormAsJSON();
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