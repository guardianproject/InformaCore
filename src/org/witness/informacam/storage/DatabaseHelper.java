package org.witness.informacam.storage;

import java.util.ArrayList;

import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.Constants.TrustedDestination;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabase.CursorFactory;
import net.sqlcipher.database.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "informa.db";
	private static final int DATABASE_VERSION = 1;
	
	static String TABLE;
	
	private enum QueryBuilders {
		INIT_INFORMA() {
			@Override
			public String[] build() {
				return new String[] {
					"CREATE TABLE " + Tables.Keys.MEDIA + " (" + BaseColumns._ID + " " +
					"integer primary key autoincrement," +
					Media.Keys.TYPE + " integer, " +
					Media.Keys.METADATA + " blob, " +
					Media.Keys.ORIGINAL_HASH + " text, " +
					Media.Keys.ANNOTATED_HASH + " text, " +
					Media.Keys.LOCATION_OF_ORIGINAL + " text, " +
					Media.Keys.LOCATION_OF_SENT + " text, " +
					Media.Keys.TRUSTED_DESTINATION_ID + " integer, " +
					Media.Keys.SHARE_VECTOR + " integer, " + 
					Media.Keys.MESSAGE_URL + " text, " +
					Media.Keys.STATUS + " integer, " + 
					Media.Keys.ALIAS + " text" +
					")",
					"CREATE TABLE " + Tables.Keys.TRUSTED_DESTINATIONS + " (" + BaseColumns._ID + " " +
					"integer primary key autoincrement," +
					TrustedDestination.Keys.DISPLAY_NAME + " text, " +
					TrustedDestination.Keys.EMAIL + " text, " +
					TrustedDestination.Keys.KEYRING_ID + " integer, " +
					TrustedDestination.Keys.URL + " text, " +
					TrustedDestination.Keys.IS_DELETABLE + " integer, " +
					TrustedDestination.Keys.CONTACT_PHOTO + " blob" +
					")",
					"CREATE TABLE " + Tables.Keys.SETUP + " (" + BaseColumns._ID + " " +
					"integer primary key autoincrement," +
					Informa.Keys.Device.LOCAL_TIMESTAMP + " integer, " +
					Informa.Keys.Device.PUBLIC_TIMESTAMP + " integer, " +
					Informa.Keys.Device.DISPLAY_NAME + " text, " +
					Settings.Device.Keys.KEYRING_ID + " integer, " +
					Settings.Device.Keys.SECRET_KEY + " blob, " + 
					Settings.Device.Keys.AUTH_KEY + " text, " + 
					Settings.Device.Keys.BASE_IMAGE + " text" +
					")",
					"CREATE TABLE " + Tables.Keys.KEYRING + " (" + BaseColumns._ID + " " +
					"integer primary key autoincrement," +
					Crypto.Keyring.Keys.ID + " integer, " +
					Crypto.Keyring.Keys.PUBLIC_KEY + " blob, " +
					Crypto.Keyring.Keys.FINGERPRINT + " text, " +
					PGP.Keys.PGP_CREATION_DATE + " integer, " +
					PGP.Keys.PGP_EXPIRY_DATE + " integer, " +
					PGP.Keys.PGP_DISPLAY_NAME + " text, " +
					PGP.Keys.PGP_EMAIL_ADDRESS + " text, " +
					Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID + " integer" +
					")",
					"CREATE TABLE " + Tables.Keys.KEYSTORE + " (" + BaseColumns._ID + " " +
					"integer primary key autoincrement," +
					Crypto.Keystore.Keys.CERTS + " blob, " +
					Crypto.Keystore.Keys.TIME_MODIFIED + " integer" +
					")"
				};
				
			}
		},
		CHECK_IF() {
			@Override
			public String[] build() {
				return new String[] {
					"SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name= '" + TABLE + "'" 
				};
			}
		};
		
		public abstract String[] build();
	}
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {}
	
	public void removeValue(SQLiteDatabase db, String[] matchKey, Object[] matchValue) {
		String query = "DELETE FROM " + TABLE + " WHERE ";
		for(int m=0; m<matchKey.length; m++) {
			if(matchValue[m].getClass().equals(String.class))
				matchValue[m] = "\"" + matchValue[m] + "\"";
			query += (matchKey[m] + "=" + matchValue[m] + " AND ");
		}
		query = query.substring(0, query.length() - 5);
		db.execSQL(query);
	}
	
	public Cursor getValue(SQLiteDatabase db, String[] values, String matchKey, Object matchValue, Object[] matchRange) {
		String select = "*";
		
		if(values != null) {
			StringBuffer sb = new StringBuffer();
			for(String v : values)
				sb.append(v + ",");
			select = sb.toString().substring(0, sb.toString().length() - 1);
		}
		
		String query = "SELECT " + select + " FROM " + getTable();
		
		if(matchKey != null) {
			if(matchValue.getClass().equals(String.class))
				matchValue = "\"" + matchValue + "\"";
		
			query += " WHERE " + matchKey + " = " + matchValue;
		}
		
		if(matchRange != null) {
			query += " BETWEEN " + matchRange[0] + " AND " + matchRange[1];
		}
		
		Cursor c = db.rawQuery(query, null);
		
		if(c != null && c.getCount() > 0) {
			return c;
		} else {
			c.close();
			return null;
		}
	}
	
	public Cursor getMultiple(SQLiteDatabase db, String[] values, String matchKey, Object[] matchValue) {
		String select = "*";
		
		if(values != null) {
			StringBuffer sb = new StringBuffer();
			for(String v : values)
				sb.append(v + ",");
			select = sb.toString().substring(0, sb.toString().length() - 1);
		}
		
		String query = "SELECT " + select + " FROM " + getTable();
		
		if(matchKey != null) {
			StringBuffer sb = new StringBuffer();
			String pattern = " OR " + matchKey + "=";
			for(Object o : matchValue) {
				if(o.getClass().equals(String.class))
					sb.append("\"" + o + "\"");
				else
					sb.append(o);
				sb.append(pattern);
			}
		
			query += " WHERE " + matchKey + " = " + sb.toString().substring(0, (sb.length() - pattern.length()));
		}
				
		Cursor c = db.rawQuery(query, null);
		
		if(c != null && c.getCount() > 0) {
			return c;
		} else {
			c.close();
			return null;
		}
	}
	
	public Cursor getValue(SQLiteDatabase db, String[] values, String matchKey, Object matchValue) {
		String select = "*";
		
		if(values != null) {
			StringBuffer sb = new StringBuffer();
			for(String v : values)
				sb.append(v + ",");
			select = sb.toString().substring(0, sb.toString().length() - 1);
		}
		
		String query = "SELECT " + select + " FROM " + getTable();
		
		if(matchKey != null) {
			if(matchValue.getClass().equals(String.class))
				matchValue = "\"" + matchValue + "\"";
		
			query += " WHERE " + matchKey + " = " + matchValue;
		}
				
		Cursor c = db.rawQuery(query, null);
		
		if(c != null && c.getCount() > 0) {
			return c;
		} else {
			c.close();
			return null;
		}
	}

	public String getTable() {
		return TABLE;
	}

	public boolean setTable(SQLiteDatabase db, String whichTable) {
		TABLE = whichTable;
		Cursor c = db.rawQuery(QueryBuilders.CHECK_IF.build()[0], null);
		if(c != null && c.getCount() > 0) {
			c.close();
			return true;
		} else {
			c.close();
			ArrayList<String> queries = new ArrayList<String>();
			if(getTable().equals(Tables.Keys.MEDIA))
				queries.add(QueryBuilders.INIT_INFORMA.build()[0]);
			else if(getTable().equals(Tables.Keys.TRUSTED_DESTINATIONS))
				queries.add(QueryBuilders.INIT_INFORMA.build()[1]);
			else if(getTable().equals(Tables.Keys.SETUP))
				queries.add(QueryBuilders.INIT_INFORMA.build()[2]);
			else if(getTable().equals(Tables.Keys.KEYRING))
				queries.add(QueryBuilders.INIT_INFORMA.build()[3]);
			else if(getTable().equals(Tables.Keys.KEYSTORE))
				queries.add(QueryBuilders.INIT_INFORMA.build()[4]);
			
			for(String q : queries)
				db.execSQL(q);
		}
		return false;
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if(oldVersion >= newVersion)
			return;
		
		String sql = null;
		if(oldVersion == 1)
			sql = "ALTER TABLE " + TABLE + " add note text;";
		if(oldVersion == 2)
			sql = "";
		
		if(sql != null)
			db.execSQL(sql);
		
	}

}
