package org.adw.launcher2.settings;

import java.net.URISyntaxException;
import java.text.Collator;
import java.util.Comparator;

import org.adw.launcher2.AppDB;
import org.adw.launcher2.IconCache;
import org.adw.launcher2.ShortcutInfo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Preferences {

	private final static String EmptyString = "";

	private static Preferences _Current = null;

	public static Preferences getInstance() {
		if (_Current == null)
			_Current = new Preferences();
		return _Current;
	}

	private SharedPreferences mPreferences = null;


	public void setContext(Context context) {
		if (context == null && mPreferences != null) {
			mPreferences = null;
		} else if (context != null) {
			mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		}
	}

	private Intent getIntent(String key) {
		String val = mPreferences.getString(PREF_SWIPE_UP_ACTION, EmptyString);
		if (val.equals(EmptyString)) {
			try {
				return Intent.parseUri(val, 0);
			} catch (URISyntaxException e) {
				return null;
			}
		} else
			return null;
	}

	static final String PREF_HOMESCREEN_ENDLESS_LOOP = "EndlessHomescreenLoop";
	static final String PREF_SWIPE_UP_ACTION = "SwipeUpAction";
	static final String PREF_SWIPE_DOWN_ACTION = "SwipeDownAction";
	static final String PREF_CURRENT_DRAWER_SORT_ORDER = "CurrentDrawerSortOrder";

	public boolean getEndlessScrolling() {
		return mPreferences.getBoolean(PREF_HOMESCREEN_ENDLESS_LOOP, true);
	}

	public Intent getSwipeUpAction() {
		return getIntent(PREF_SWIPE_UP_ACTION);
	}

	public Intent getSwipeDownAction() {
		return getIntent(PREF_SWIPE_DOWN_ACTION);
	}

	private static final int SORT_BY_NAME = 1;
	private static final int SORT_BY_LAUNCH_COUNT = 2;

    private static final Collator sCollator = Collator.getInstance();

    private Comparator<ShortcutInfo> mNameComparator = null;

    private Comparator<ShortcutInfo> getAppNameComparator(IconCache iconCache) {
    	if (mNameComparator == null) {
    		final IconCache myIconCache = iconCache;
    		mNameComparator = new Comparator<ShortcutInfo>() {
    			@Override
	    		public final int compare(ShortcutInfo a, ShortcutInfo b) {
	    			return sCollator.compare(a.getTitle(myIconCache), b.getTitle(myIconCache));
	    		}
	    	};
    	}
    	return mNameComparator;
    }

    private Comparator<ShortcutInfo> mLaunchCountComparator = null;

    private Comparator<ShortcutInfo> getLaunchCountComparator(AppDB appdb) {
    	if (mLaunchCountComparator == null) {
    		final AppDB myAppDB = appdb;
    		mLaunchCountComparator = new Comparator<ShortcutInfo>() {
				@Override
				public int compare(ShortcutInfo a, ShortcutInfo b) {
					return myAppDB.getLaunchCounter(a) - myAppDB.getLaunchCounter(b);
				}
			};
    	}
    	return mLaunchCountComparator;
    }

    public Comparator<ShortcutInfo> getCurrentDrawerComparator(AppDB appDB, IconCache iconCache) {
    	int currentMode = mPreferences.getInt(PREF_CURRENT_DRAWER_SORT_ORDER, SORT_BY_NAME);
    	switch(currentMode) {
    		case SORT_BY_NAME:
    			return getAppNameComparator(iconCache);
    		case SORT_BY_LAUNCH_COUNT:
    			return getLaunchCountComparator(appDB);
    		default:
    			return null;
    	}

    }

}
