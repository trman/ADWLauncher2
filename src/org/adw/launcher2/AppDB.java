package org.adw.launcher2;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class AppDB extends BroadcastReceiver {
	private static AppDB sInstance = null;

	public static AppDB getInstance() {
		return sInstance;
	}

	private final Object mLock = new Object();

	private Context mContext;
	private DatabaseHelper mDBHelper;

	public AppDB() {
		sInstance = this;
	}

	void initialize(Launcher launcher) {
		synchronized (mLock) {
			mContext = launcher;
			mDBHelper = new DatabaseHelper();
			mDBHelper.getReadableDatabase().close();
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
			return addNewEntry(name, null);
		}
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

	private long addNewEntry(ComponentName name, ContentValues defaultValues) {
		if (mDBHelper == null)
			mDBHelper = new DatabaseHelper();
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
			final ContentValues cvs;
			if (defaultValues == null)
				cvs = new ContentValues();
			else
				cvs = defaultValues;
			cvs.put(Columns.COMPONENT_NAME, name.flattenToString());
			cvs.put(Columns.LAUNCH_COUNT, 0);
			return db.insert(Tables.AppInfos, null, cvs);
		}
		finally {
			db.close();
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		mContext = context;
		// This code will run outside of the launcher process!!!!!
		final String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            final String packageName = intent.getData().getSchemeSpecificPart();
            final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            if (packageName == null || packageName.length() == 0) {
                // they sent us a bad intent
                return;
            }

            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                PackageChanged(packageName);
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (!replacing) {
                    PackageRemoved(packageName);
                }
                // else, we are replacing the package, so a PACKAGE_ADDED will be sent
                // later, we will update the package at this time
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                if (!replacing) {
                	PackageAdded(packageName);
                } else {
                    PackageChanged(packageName);
                }
            }
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
            String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            AvailableAppsChanged(packages, true);
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
            String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            AvailableAppsChanged(packages, false);
        }
	}

	private void PackageAdded(String aPackage) {
		final PackageManager packageManager = mContext.getPackageManager();
        AddResolveInfos(packageManager, findActivitiesForPackage(packageManager, aPackage));
	}

	private void PackageChanged(String aPackage) {
		Log.d("BOOMBULER", "Package changed: "+aPackage);
	}

	private void PackageRemoved(String aPackage) {
		Log.d("BOOMBULER", "Package removed: "+aPackage);
	}

	private void AvailableAppsChanged(String[] packages, boolean active) {
		for (String pack : packages) {
			Log.d("BOOMBULER", "package "+pack+": "+active);
		}
	}

	private static Bitmap getIconFromCursor(Cursor c, int iconIndex) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

	public List<ShortcutInfo> getApps() {
		ArrayList<ShortcutInfo> result = new ArrayList<ShortcutInfo>();
		DatabaseHelper dbHelper = new DatabaseHelper();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		try {
			Cursor c = db.query(Tables.AppInfos, new String[] {
					Columns.COMPONENT_NAME,
					Columns.ICON,
					Columns.TITLE
			}, null, null, null, null, null);
			try {
				c.moveToFirst();
				while(!c.isAfterLast()) {

					Bitmap icon = getIconFromCursor(c, c.getColumnIndex(Columns.ICON));
					String cnStr = c.getString(c.getColumnIndex(Columns.COMPONENT_NAME));
					String title = c.getString(c.getColumnIndex(Columns.TITLE));
					if (title != null) {
						ShortcutInfo info = new ShortcutInfo(
								title,
								ComponentName.unflattenFromString(cnStr),
								icon);
						result.add(info);
					}

					c.moveToNext();
				}
			}
			finally {
				c.close();
			}
		}
		finally {
			db.close();
		}
		return result;
	}

    private List<ResolveInfo> findActivitiesForPackage(PackageManager packageManager, String packageName) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        return apps != null ? apps : new ArrayList<ResolveInfo>();
    }

    private void AddResolveInfos(PackageManager packageManager, List<ResolveInfo> infos) {
    	for(ResolveInfo info : infos) {
        	ComponentName componentName = new ComponentName(
                    info.activityInfo.applicationInfo.packageName,
                    info.activityInfo.name);

        	String title = info.loadLabel(packageManager).toString();

            if (title == null) {
                title = info.activityInfo.name;
            }
            Bitmap icon = Utilities.createIconBitmap(
                    info.activityInfo.loadIcon(packageManager), mContext);

            ContentValues values = new ContentValues();
            values.put(Columns.TITLE, title);
            ItemInfo.writeBitmap(values, icon);
            addNewEntry(componentName, values);
        }
    }


	private static class Columns {
		public static final String ID = "_id";
		public static final String COMPONENT_NAME = "componentname";
		public static final String LAUNCH_COUNT = "launchcount";
		public static final String TITLE = "title";
		public static final String ICON = "icon";
	}

	private static class Tables {
		public static final String AppInfos = "appinfos";
	}

	private class DatabaseHelper extends SQLiteOpenHelper {

	    private static final String DATABASE_NAME = "apps.db";
	    private static final int DATABASE_VERSION = 1;


        DatabaseHelper() {
            super(AppDB.this.mContext, DATABASE_NAME, null, DATABASE_VERSION);
        }

		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.d("BOOMBULER", "creatingDB");
			db.execSQL("CREATE TABLE "+Tables.AppInfos+" (" +
					Columns.ID + " INTEGER PRIMARY KEY," +
					Columns.COMPONENT_NAME + " TEXT," +
					Columns.TITLE + " TEXT," +
					Columns.ICON + " BLOB," +
					Columns.LAUNCH_COUNT + " INTEGER" +
                    ");");
			CreateIndexFor(db, Tables.AppInfos, Columns.COMPONENT_NAME);
			Thread thrd = new Thread() {
				@Override
				public void run() {
					PackageManager pMan = mContext.getPackageManager();
		            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		            AddResolveInfos(pMan, pMan.queryIntentActivities(mainIntent, 0));
				}
			};
			thrd.start();
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
