package org.adw.launcher2;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDB {
	private static AppDB sInstance = null;

	public static AppDB getInstance() {
		return sInstance;
	}

	private final Object mLock = new Object();

	private Launcher mLauncher;
	private DatabaseHelper mDBHelper;

	public AppDB() {
		sInstance = this;
	}

	void initialize(Launcher launcher) {
		synchronized (mLock) {
			mLauncher = launcher;
			mDBHelper = new DatabaseHelper();
		}
	}

	public long getId(ComponentName name) {
		synchronized (mLock) {
			SQLiteDatabase db = mDBHelper.getReadableDatabase();
			try {
				Cursor c = db.query(Tables.AppInfos, new String[] { Columns.ID },
						Columns.COMPONENT_NAME + "=?", new String[] { name.toString() }, null, null, null);
				try {
					c.moveToFirst();
					if (!c.isAfterLast()) {
						return c.getLong(0);
					}
				}
				finally {
					c.close();
				}
			}
			finally {
				db.close();
			}
			// still not returned? ok then it is a new entry!
			return addNewEntry(name);
		}
	}


	public void incrementLaunchCounter(ApplicationInfo info) {
		incrementLaunchCounter(info.componentName);
	}

	public void incrementLaunchCounter(ShortcutInfo info) {
		if (info.intent.getAction().equals(Intent.ACTION_MAIN) &&
				info.intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
			incrementLaunchCounter(info.intent.getComponent());
		}
	}

	private void incrementLaunchCounter(ComponentName name) {
		synchronized (mLock) {
			long id = getId(name);
			SQLiteDatabase db = mDBHelper.getWritableDatabase();
			try {
				db.execSQL("UPDATE  "+Tables.AppInfos+" SET "+
						Columns.LAUNCH_COUNT + "=" + Columns.LAUNCH_COUNT + " + 1 WHERE "+
						Columns.ID + "="+ id);
			}
			finally {
				db.close();
			}
		}
	}

	private long addNewEntry(ComponentName name) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			ContentValues cvs = new ContentValues();
			cvs.put(Columns.COMPONENT_NAME, name.toString());
			cvs.put(Columns.LAUNCH_COUNT, 0);
			return db.insert(Tables.AppInfos, null, cvs);
		}
		finally {
			db.close();
		}
	}


	private static class Columns {
		public static final String ID = "_id";
		public static final String COMPONENT_NAME = "componentname";
		public static final String LAUNCH_COUNT = "launchcount";
	}

	private static class Tables {
		public static final String AppInfos = "appinfos";
	}

	private class DatabaseHelper extends SQLiteOpenHelper {

	    private static final String DATABASE_NAME = "apps.db";
	    private static final int DATABASE_VERSION = 1;


        DatabaseHelper() {
            super(AppDB.this.mLauncher, DATABASE_NAME, null, DATABASE_VERSION);
        }

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE "+Tables.AppInfos+" (" +
					Columns.ID + " INTEGER PRIMARY KEY," +
					Columns.COMPONENT_NAME + " TEXT," +
					Columns.LAUNCH_COUNT + " INTEGER" +
                    ");");
			CreateIndexFor(db, Tables.AppInfos, Columns.COMPONENT_NAME);
		}

		private void CreateIndexFor(SQLiteDatabase db, String table, String column) {
			db.execSQL("CREATE INDEX idx_" + table + "_"+ column +" ON "+table+"("+column+");");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub

		}

	}
}
