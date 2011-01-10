package org.adw.launcher2;

import org.adw.launcher2.LauncherProvider.SqlArguments;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class AppDBProvider extends ContentProvider {
	static final String AUTHORITY = "org.adw.launcher2.appdb";


	private SQLiteOpenHelper mOpenHelper;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        return db.delete(args.table, args.where, args.args);
	}

	@Override
	public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long rowId = db.insert(args.table, null, initialValues);
        if (rowId <= 0)
        	return null;

        return ContentUris.withAppendedId(uri, rowId);
	}

	@Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try {
        	db.beginTransaction();
        	DoBulkInsert(db, args.table, values);
        	db.setTransactionSuccessful();
        } finally {
        	db.endTransaction();
        	db.close();
        }
        return values.length;
    }

	private void DoBulkInsert(SQLiteDatabase db, String table, ContentValues[] values) {
        int numValues = values.length;
        for (int i = 0; i < numValues; i++) {
            if (db.insert(table, null, values[i]) < 0)
            	return;
        }
	}

	@Override
	public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try {
          Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
          result.setNotificationUri(getContext().getContentResolver(), uri);
        } finally {
        	db.close();
        }

        return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        return db.update(args.table, values, args.where, args.args);
	}

	private class DatabaseHelper extends SQLiteOpenHelper {

	    private static final String DATABASE_NAME = "apps.db";
	    private static final int DATABASE_VERSION = 1;

	    private final Context mContext;


	    DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE "+AppDB.APPINFOS+" (" +
					AppDB.AppInfos.ID + " INTEGER PRIMARY KEY," +
					AppDB.AppInfos.COMPONENT_NAME + " TEXT," +
					AppDB.AppInfos.TITLE + " TEXT," +
					AppDB.AppInfos.ICON + " BLOB," +
					AppDB.AppInfos.LAUNCH_COUNT + " INTEGER" +
                    ");");
			CreateIndexFor(db, AppDB.APPINFOS, AppDB.AppInfos.COMPONENT_NAME);

			PackageManager packageManager = mContext.getPackageManager();
            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            DoBulkInsert(db, AppDB.APPINFOS,
            	AppDB.ResolveInfosToContentValues(mContext,
            		packageManager.queryIntentActivities(mainIntent, 0)));
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
