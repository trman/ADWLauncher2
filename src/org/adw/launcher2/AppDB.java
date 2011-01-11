package org.adw.launcher2;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

public class AppDB extends BroadcastReceiver {
	private static final long INVALID_ID = -1;
	private static final String PACKAGE_SEPERATOR = "/";
	public static final String INTENT_DB_CHANGED = "org.adw.launcher2.app_db_changed";
	public static final String EXTRA_ADDED = "added";
	public static final String EXTRA_DELETED_PACKAGE = "deleted_package";
	public static final String EXTRA_DELETED_COMPONENT_NAMES = "deleted_cnames";
	public static final String EXTRA_UPDATED = "updated";

	private Context mContext;
	private final IconCache mIconCache;

	public AppDB(Context context, IconCache iconCache) {
		mContext = context;
		mIconCache = iconCache;
	}

	@Deprecated
	public AppDB() {
		mIconCache = null;
		// Only for Broadcast reciever!
	}


	public long getId(ComponentName name) {
		ContentResolver cr = mContext.getContentResolver();
		Cursor c = cr.query(AppInfos.CONTENT_URI,
				new String[] { AppInfos.ID },
				AppInfos.COMPONENT_NAME + "=?",
				new String[] { name.toString() }, null);
		try {
			c.moveToFirst();
			if (!c.isAfterLast()) {
				return c.getLong(0);
			}
		}
		finally {
			c.close();
		}
		return INVALID_ID;
	}

	public void incrementLaunchCounter(ShortcutInfo info) {
		if (info != null && info.intent != null) {
			String action = info.intent.getAction();
			if (Intent.ACTION_MAIN.equals(action) &&
					info.intent.hasCategory(Intent.CATEGORY_LAUNCHER))
				incrementLaunchCounter(info.intent.getComponent());
		}
	}

	private void incrementLaunchCounter(ComponentName name) {
		ContentResolver cr = mContext.getContentResolver();
		Cursor c = cr.query(
				AppInfos.APP_LAUNCHED_URI.buildUpon()
					.appendEncodedPath(name.flattenToString()).build(),
				null, null, null, null);
		if (c != null)
			c.close();
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
		final PackageManager packageManager = mContext.getPackageManager();

		List<ExtResolveInfo> addedApps = new LinkedList<ExtResolveInfo>();
		List<DBInfo> removedApps = new LinkedList<DBInfo>();
		HashMap<ExtResolveInfo, DBInfo> updatedApps = new HashMap<ExtResolveInfo, DBInfo>();

        List<ExtResolveInfo> pmInfos = toExtInfos(findActivitiesForPackage(packageManager, aPackage));
        ContentResolver cr = mContext.getContentResolver();
        Cursor c = queryAppsFromPackage(
				new String[] {AppInfos.ID, AppInfos.COMPONENT_NAME},
				aPackage);
        List<DBInfo> dbInfos = toDBInfos(c);
        c.close();

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

    	// removing is a little harder:
    	DestroyItems(removedApps);
    	if (sendIntent)
    		modelIntent.putExtra(EXTRA_DELETED_COMPONENT_NAMES, getPackageNames(removedApps));

    	// ok then updating is left:
    	long[] updatedIds = new long[updatedApps.size()];
    	int i = 0;
    	for (ExtResolveInfo pmInfo : updatedApps.keySet()) {
    		DBInfo dbinfo = updatedApps.get(pmInfo);

    		ResolveInfo rInfo = pmInfo.getResolveInfo();
            Bitmap icon = Utilities.createIconBitmap(
                    rInfo.loadIcon(packageManager), mContext);

            ContentValues values = new ContentValues();
            values.put(AppInfos.TITLE, rInfo.loadLabel(packageManager).toString());
            ItemInfo.writeBitmap(values, icon);

    		cr.update(AppInfos.CONTENT_URI, values, AppInfos.ID + " = ?",
    				new String[] { String.valueOf(dbinfo.getId()) } );
    		updatedIds[i++] = dbinfo.getId();
    	}
    	if (i > 0)
    		modelIntent.putExtra(EXTRA_UPDATED, updatedIds);

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
	    List<DBInfo> infos = new LinkedList<DBInfo>();
	    Cursor c = queryAppsFromPackage(new String[] { AppInfos.ID, AppInfos.COMPONENT_NAME }, aPackage);
	    try {
	    	c.moveToFirst();
			c.getColumnIndex(AppInfos.ID);
			c.getColumnIndex(AppInfos.COMPONENT_NAME);
			while(!c.isAfterLast()) {
				DBInfo info = new DBInfo(c);
				infos.add(info);
				c.moveToNext();
			}
		} finally {
			c.close();
		}

		DestroyItems( infos);
		// notify the LauncherModel too!
		Intent deleteIntent = new Intent(INTENT_DB_CHANGED);
		// remove all items from the package!
		deleteIntent.putExtra(EXTRA_DELETED_PACKAGE, aPackage);
		mContext.sendBroadcast(deleteIntent);
	}

	private void DestroyItems(List<DBInfo> infos) {
		if (infos.size() > 0) {
			String deleteFlt = getAppIdFilter(getIds(infos));
			ContentResolver cr = mContext.getContentResolver();

			cr.delete(AppInfos.CONTENT_URI, deleteFlt, null);
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

	private Cursor queryAppsFromPackage(String[] columns, String aPackage) {
		aPackage = aPackage + PACKAGE_SEPERATOR;
		String pkgflt = "substr("+AppInfos.COMPONENT_NAME + ",1,"+ aPackage.length() +") = ?";
		final ContentResolver cr = mContext.getContentResolver();
		return cr.query(AppInfos.CONTENT_URI,
				columns, pkgflt, new String[] { aPackage }, null);
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
			sb.append(AppInfos.ID);
			sb.append("=");
			sb.append(appIds[i]);
		}
		return sb.toString();
	}

	public List<ShortcutInfo> getApps(long[] appIds) {
		ArrayList<ShortcutInfo> result = new ArrayList<ShortcutInfo>();
		ContentResolver cr = mContext.getContentResolver();

		Cursor c = cr.query(AppInfos.CONTENT_URI, new String[] {
				AppInfos.COMPONENT_NAME,
				AppInfos.ICON,
				AppInfos.TITLE
		}, getAppIdFilter(appIds), null, null);
		try {
			c.moveToFirst();
			final int iconIdx = c.getColumnIndex(AppInfos.ICON);
			final int cnIdx = c.getColumnIndex(AppInfos.COMPONENT_NAME);
			final int titleIdx = c.getColumnIndex(AppInfos.TITLE);

			while(!c.isAfterLast()) {
				Bitmap icon = getIconFromCursor(c, iconIdx);
				String cnStr = c.getString(cnIdx);
				String title = c.getString(titleIdx);
				ComponentName cname = ComponentName.unflattenFromString(cnStr);
				if (mIconCache != null)
					mIconCache.addToCache(cname, title, icon);
				if (title != null) {
					ShortcutInfo info = new ShortcutInfo(
							title,
							cname);
					result.add(info);
				}
				c.moveToNext();
			}
		}
		finally {
			c.close();
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

    static ContentValues[] ResolveInfosToContentValues(Context context, List<?> infos) {
    	PackageManager packageManager = context.getPackageManager();

    	ContentValues[] result = new ContentValues[infos.size()];
    	int i = 0;
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
                    info.activityInfo.loadIcon(packageManager), context);

            ContentValues values = new ContentValues();
            values.put(AppInfos.TITLE, title);
            ItemInfo.writeBitmap(values, icon);
            values.put(AppInfos.COMPONENT_NAME, componentName.flattenToString());
			values.put(AppInfos.LAUNCH_COUNT, 0);
            result[i++] = values;
    	}
    	return result;
    }

    private void AddResolveInfos(PackageManager packageManager, List<?> infos) {
    	ContentResolver cr = mContext.getContentResolver();

    	long[] added = new long[infos.size()];

    	int i = 0;
    	for(ContentValues values : ResolveInfosToContentValues(mContext, infos)) {
    		added[i++] = ContentUris.parseId(cr.insert(AppInfos.CONTENT_URI, values));
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
    		mId = c.getLong(c.getColumnIndex(AppInfos.ID));
    		mComponentName = c.getString(c.getColumnIndex(AppInfos.COMPONENT_NAME));
    	}

    	public long getId(){
    		return mId;
    	}

    	public String getComponentName() {
    		return mComponentName;
    	}
    }

    public static final String APPINFOS = "appinfos";

	public static class AppInfos {
		public static final String ID = "_id";
		public static final String COMPONENT_NAME = "componentname";
		public static final String LAUNCH_COUNT = "launchcount";
		public static final String LAST_LAUNCHED = "lastlaunched";
		public static final String TITLE = "title";
		public static final String ICON = "icon";

        static final Uri CONTENT_URI = Uri.parse("content://" +
                AppDBProvider.AUTHORITY + "/" + APPINFOS);

        static final Uri APP_LAUNCHED_URI = Uri.parse("content://"+
        		AppDBProvider.AUTHORITY + "/launched");

        /**
         * The content:// style URL for a given row, identified by its id.
         *
         * @param id The row id.
         * @param notify True to send a notification is the content changes.
         *
         * @return The unique content URL for the specified row.
         */
        static Uri getContentUri(long id) {
            return Uri.parse("content://" + AppDBProvider.AUTHORITY +
                    "/" + APPINFOS + "/" + id);
        }

	}

}
