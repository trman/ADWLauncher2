package org.adw.launcher2;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
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

	private static final long INVALID_ID = -1;
	private static final String PACKAGE_SEPERATOR = "/";
	public static final String INTENT_DB_CHANGED = "org.adw.launcher2.app_db_changed";
	public static final String EXTRA_ADDED = "added";
	public static final String EXTRA_DELETED_PACKAGE = "deleted_package";
	public static final String EXTRA_DELETED_COMPONENT_NAMES = "deleted_cnames";

	private final Object mLock = new Object();

	private Context mContext;
	private DatabaseHelper mDBHelper;
	private final IconCache mIconCache;

	public AppDB(IconCache iconCache) {
		sInstance = this;
		mIconCache = iconCache;
	}

	@Deprecated
	public AppDB() {
		mIconCache = null;
		// Only for Broadcast reciever!
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
			return INVALID_ID;
		}
	}

	public void incrementLaunchCounter(ShortcutInfo info) {
		synchronized(mLock) {
			if (info.intent.getAction().equals(Intent.ACTION_MAIN) &&
					info.intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
				incrementLaunchCounter(info.intent.getComponent());
			}
		}
	}

	private void incrementLaunchCounter(ComponentName name) {
		long id = getId(name);
		if (id != INVALID_ID) {
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

	private long addNewEntry(SQLiteDatabase db, ComponentName name, ContentValues defaultValues) {
		if (mDBHelper == null)
			mDBHelper = new DatabaseHelper();
		final boolean dbCreated;
		if (db == null) {
			db = mDBHelper.getWritableDatabase();
			dbCreated = true;
		} else
			dbCreated = false;

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
			if (dbCreated)
				db.close();
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (mContext == null)
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
        }
	}

	private void PackageAdded(String aPackage) {
		final PackageManager packageManager = mContext.getPackageManager();
        AddResolveInfos(packageManager, findActivitiesForPackage(packageManager, aPackage));
	}

	private void PackageChanged(String aPackage) {
		Log.d("BOOMBULER", "Package changed: "+aPackage);
		if (mDBHelper == null)
			mDBHelper = new DatabaseHelper();

		final PackageManager packageManager = mContext.getPackageManager();

		List<ExtResolveInfo> addedApps = new LinkedList<ExtResolveInfo>();
		List<DBInfo> removedApps = new LinkedList<DBInfo>();
		HashMap<ExtResolveInfo, DBInfo> updatedApps = new HashMap<ExtResolveInfo, DBInfo>();

        List<ExtResolveInfo> pmInfos = toExtInfos(findActivitiesForPackage(packageManager, aPackage));
        List<DBInfo> dbInfos = toDBInfos(queryAppsFromPackage(null, new String[] {Columns.ID, Columns.COMPONENT_NAME}, aPackage));

        // find removed / updated apps
        for(DBInfo dbi : dbInfos) {
        	boolean found = false;
        	for (ExtResolveInfo pmi : pmInfos) {
	        	if (pmi.getComponentName().equals(dbi.getComponentName())) {
	        		found = true;
	        		updatedApps.put(pmi, dbi); // update dbi from pmi later
	        		break;
	        	}
        	}
        	if (!found) {
        		removedApps.add(dbi); // app is no longer installed!
        	}
        }
        for (ExtResolveInfo pmi : pmInfos) {
        	if (updatedApps.containsKey(pmi))
        		continue; // alread in updateable apps
        	addedApps.add(pmi); // not updated, not removed so it must be added ;-)
        }
        Intent modelIntent = new Intent(INTENT_DB_CHANGED);
        boolean sendIntent = removedApps.size() > 0 || updatedApps.size() > 0;

        // Ok we got all needed infos so lets start the party:
        AddResolveInfos(packageManager, addedApps); // adding is easy!

        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        try {
        	// removing is a little harder:
        	DestroyItems(db, removedApps);
        	if (sendIntent)
        		modelIntent.putExtra(EXTRA_DELETED_COMPONENT_NAMES, getPackageNames(removedApps));
        	// ok then updating is left:
        	// TODO_BOOMBULER
        } finally {
        	db.close();
        }

        // Notify Model:
        mContext.sendBroadcast(modelIntent);
	}

	private List<ExtResolveInfo> toExtInfos(List<ResolveInfo> list) {
		List<ExtResolveInfo> result = new ArrayList<ExtResolveInfo>(list.size());
		for(ResolveInfo info : list) {
			result.add(new ExtResolveInfo(info));
		}
		return result;
	}

	private List<DBInfo> toDBInfos(Cursor c) {
		List<DBInfo> result = new LinkedList<DBInfo>();
		if (c.moveToFirst()) {
			while(!c.isAfterLast()) {
				result.add(new DBInfo(c));
				c.moveToNext();
			}
		}
		return result;
	}

	private void PackageRemoved(String aPackage) {
		if (mDBHelper == null)
			mDBHelper = new DatabaseHelper();
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try {
		    List<DBInfo> infos = new LinkedList<DBInfo>();
		    Cursor c = queryAppsFromPackage(db, new String[] { Columns.ID, Columns.COMPONENT_NAME }, aPackage);
		    try {
		    	c.moveToFirst();
				c.getColumnIndex(Columns.ID);
				c.getColumnIndex(Columns.COMPONENT_NAME);
				while(!c.isAfterLast()) {
					DBInfo info = new DBInfo(c);
					infos.add(info);
					c.moveToNext();
				}
			} finally {
				c.close();
			}

			DestroyItems(db, infos);
			// notify the LauncherModel too!
			Intent deleteIntent = new Intent(INTENT_DB_CHANGED);
			// remove all items from the package!
			deleteIntent.putExtra(EXTRA_DELETED_PACKAGE, aPackage);
			mContext.sendBroadcast(deleteIntent);
		} finally {
			db.close();
		}
	}

	private void DestroyItems(SQLiteDatabase db, List<DBInfo> infos) {
		if (infos.size() > 0) {
			String deleteFlt = getAppIdFilter(getIds(infos));
			db.delete(Tables.AppInfos, deleteFlt, null);

			RemoveShortcutsFromWorkspace(infos);
		}
	}

	static boolean arrayContains(String[] array, String value) {
		for (String itm : array) {
			if (itm.equals(value))
				return true;
		}
		return false;
	}

	private static boolean InfosContains(List<DBInfo> infos, String value) {
		for (DBInfo itm : infos) {
			if (value.equals(itm.getComponentName()))
				return true;
		}
		return false;
	}

	private static long[] getIds(List<DBInfo> infos) {
		long[] result = new long[infos.size()];
		for (int i = 0; i < infos.size(); i++) {
			result[i] = infos.get(i).getId();
		}
		return result;
	}

	private static String[] getPackageNames(List<DBInfo> infos) {
		String[] result = new String[infos.size()];
		for (int i = 0; i < infos.size(); i++) {
			result[i] = infos.get(i).getComponentName();
		}
		return result;
	}

	private void RemoveShortcutsFromWorkspace(List<DBInfo> infos) {
		final ContentResolver cr = mContext.getContentResolver();

        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
            new String[] { LauncherSettings.Favorites._ID,
        		LauncherSettings.Favorites.INTENT },
        		LauncherSettings.Favorites.INTENT + " is not null", null, null);
        long[] ids = null;
        try {
	        if (c != null && c.moveToFirst()) {
	        	// prepare the dirty work!
	        	ids = new long[c.getCount()];
	        	int IDColumnIndex = c.getColumnIndex(LauncherSettings.Favorites._ID);
	        	int IntentColumnIndex = c.getColumnIndex(LauncherSettings.Favorites.INTENT);
	        	int idx = 0;
	        	while (!c.isAfterLast())
	        	{
	        		String intentStr = c.getString(IntentColumnIndex);
	        		try {
		        		Intent intent = Intent.parseUri(intentStr, 0);
		        		if (intent != null) {

		        			ComponentName cname = intent.getComponent();
		        			if (cname != null ) {
			        			String cnameStr = cname.flattenToString();
			        			if (InfosContains(infos, cnameStr)) {
			        				c.getLong(IDColumnIndex);
			        				ids[idx++] = c.getLong(IDColumnIndex);
			        			}else
			        				ids[idx++] = INVALID_ID;
		        			} else {
		        				ids[idx++] = INVALID_ID;
		        			}
		        		} else
		        			ids[idx++] = INVALID_ID;
	        		}
	        		catch(URISyntaxException expt) {
	        			ids[idx++] = INVALID_ID;
	        		}

	        		c.moveToNext();
	        	}
	        }
        } finally {
        	c.close();
        }
        if (ids != null) {
        	for (long id : ids) {
        		if (id != INVALID_ID)
        			cr.delete(LauncherSettings.Favorites.getContentUri(id, false), null, null);
        	}
        }
	}

	private Cursor queryAppsFromPackage(SQLiteDatabase db, String[] columns, String aPackage) {
		aPackage = aPackage + PACKAGE_SEPERATOR;
		String pkgflt = "substr("+Columns.COMPONENT_NAME + ",1,"+ aPackage.length() +") = ?";
		return db.query(Tables.AppInfos,
				columns,
				pkgflt, new String[] { aPackage },
				null, null, null);
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
		return getApps(null);
	}

	private String getAppIdFilter(long[] appIds) {
		if (appIds == null)
			return null;
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < appIds.length; i++) {
			if (i > 0)
				sb.append(" or ");
			sb.append(Columns.ID);
			sb.append("=");
			sb.append(appIds[i]);
		}
		return sb.toString();
	}

	public List<ShortcutInfo> getApps(long[] appIds) {
		synchronized(mLock) {
			ArrayList<ShortcutInfo> result = new ArrayList<ShortcutInfo>();
			DatabaseHelper dbHelper = new DatabaseHelper();
			SQLiteDatabase db = dbHelper.getReadableDatabase();
			try {
				Cursor c = db.query(Tables.AppInfos, new String[] {
						Columns.COMPONENT_NAME,
						Columns.ICON,
						Columns.TITLE
				}, getAppIdFilter(appIds), null, null, null, null);
				try {
					c.moveToFirst();
					while(!c.isAfterLast()) {

						Bitmap icon = getIconFromCursor(c, c.getColumnIndex(Columns.ICON));
						String cnStr = c.getString(c.getColumnIndex(Columns.COMPONENT_NAME));
						String title = c.getString(c.getColumnIndex(Columns.TITLE));
						ComponentName cname = ComponentName.unflattenFromString(cnStr);
						if (mIconCache != null)
							mIconCache.addToCache(cname, title, icon);
						if (title != null) {
							ShortcutInfo info = new ShortcutInfo(
									title,
									cname,
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
	}

    private List<ResolveInfo> findActivitiesForPackage(PackageManager packageManager, String packageName) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        return apps != null ? apps : new ArrayList<ResolveInfo>();
    }

    private void AddResolveInfos(PackageManager packageManager, List<?> infos) {
    	long[] added = new long[infos.size()];
    	int i = 0;
    	if (mDBHelper == null)
    		mDBHelper = new DatabaseHelper();
    	SQLiteDatabase db = mDBHelper.getWritableDatabase();
    	try
    	{
    		db.beginTransaction();
	    	for(Object oinfo : infos) {
	    		final ResolveInfo info;
	    		if (oinfo instanceof ResolveInfo)
	    			info = (ResolveInfo)oinfo;
	    		else if (oinfo instanceof ExtResolveInfo)
	    			info = ((ExtResolveInfo)oinfo).getResolveInfo();
	    		else
	    			continue;
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
	            added[i++] = addNewEntry(db, componentName, values);
	    	}
	    	db.setTransactionSuccessful();
    	}
    	finally {
    		db.endTransaction();
    		db.close();
    	}


		Intent updateIntent = new Intent(INTENT_DB_CHANGED);
		updateIntent.putExtra("added", added);
		mContext.sendBroadcast(updateIntent);
    }

    private class ExtResolveInfo {
    	private final ResolveInfo mResolveInfo;
    	private final String mComponentName;

    	public ExtResolveInfo(ResolveInfo info) {
    		mResolveInfo = info;
        	mComponentName = new ComponentName(
                    info.activityInfo.applicationInfo.packageName,
                    info.activityInfo.name).flattenToString();
    	}

    	public String getComponentName() {
    		return mComponentName;
    	}
    	public ResolveInfo getResolveInfo() {
    		return mResolveInfo;
    	}
    }

    private class DBInfo {
    	private final long mId;
    	private final String mComponentName;

    	public DBInfo(Cursor c) {
    		mId = c.getLong(c.getColumnIndex(Columns.ID));
    		mComponentName = c.getString(c.getColumnIndex(Columns.COMPONENT_NAME));
    	}

    	public long getId(){
    		return mId;
    	}

    	public String getComponentName() {
    		return mComponentName;
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
