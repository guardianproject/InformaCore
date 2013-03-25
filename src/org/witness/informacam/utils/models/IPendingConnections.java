package org.witness.informacam.utils.models;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.utils.Constants.Models;

import android.util.Log;

public class IPendingConnections extends Model {
	public List<IConnection> queue = null;
}
