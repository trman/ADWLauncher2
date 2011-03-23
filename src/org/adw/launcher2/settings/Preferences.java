package org.adw.launcher2.settings;

import java.net.URISyntaxException;

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

	public boolean getEndlessScrolling() {
		return mPreferences.getBoolean(PREF_HOMESCREEN_ENDLESS_LOOP, true);
	}

	public Intent getSwipeUpAction() {
		return getIntent(PREF_SWIPE_UP_ACTION);
	}

	public Intent getSwipeDownAction() {
		return getIntent(PREF_SWIPE_DOWN_ACTION);
	}
}
