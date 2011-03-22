/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.adw.launcher2;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
class ShortcutInfo extends IconItemInfo {
    private static final String ANDROID_SETTINGS_PACKAGE = "com.android.settings";
    private static final String ANDROID_MARKET_PACKAGE = "com.android.vending";

    private static CharSequence mAppInfoLabel;
    private static Drawable mMarketIcon;
    private static CharSequence mMarketLabel;
    
    /**
     * The intent used to start the application.
     */
    Intent intent;

    ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    public ShortcutInfo(ShortcutInfo info) {
        super(info);
        intent = new Intent(info.intent);
    }

    public ShortcutInfo(ComponentName componentName) {
    	this.container = ItemInfo.NO_ID;
    	this.mTitle = null;
    	this.setActivity(componentName, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @Override
    public Bitmap getIcon(IconCache iconCache) {
        if (mIcon == null) {
            return iconCache.getIcon(this.intent);
        }
        return mIcon;
    }

    /**
     * Creates the application intent based on a component name and various launch flags.
     * Sets {@link #itemType} to {@link LauncherSettings.BaseLauncherColumns#ITEM_TYPE_APPLICATION}.
     *
     * @param className the class name of the component representing the intent
     * @param launchFlags the launch flags
     */
    final void setActivity(ComponentName className, int launchFlags) {
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(className);
        intent.setFlags(launchFlags);
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String uri = intent != null ? intent.toUri(0) : null;
        values.put(LauncherSettings.BaseLauncherColumns.INTENT, uri);
    }

    @Override
    public String toString() {
        return "ShortcutInfo(title=" + mTitle + ")";
    }

    @Override
    void unbind() {
        super.unbind();
    }

    @Override
	public CharSequence getTitle(IconCache mIconCache) {
    	if (mTitle == null)
    		return mIconCache.getTitle(this.intent);
    	return mTitle;
    }

    public static void dumpShortcutInfoList(String tag, String label,
            ArrayList<ShortcutInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (ShortcutInfo info: list) {
            Log.d(tag, "   title=\"" + info.mTitle + " icon=" + info.mIcon);
        }
    }

	private static final int ACTION_DELETE = 1;
    private static final int ACTION_APPINFO = 2;
    private static final int ACTION_MARKET = 3;


	@Override
	public void executeAction(EditAction action, View view, Launcher launcher) {
		switch(action.getId()) {
			case ACTION_DELETE: {
				launcher.removeDesktopItem(this);
				LauncherModel.deleteItemFromDatabase(launcher, this);
			} break;
			case ACTION_APPINFO: {
                try
                {
                    String appPackage = intent.getComponent().getPackageName();
                    Intent intent = new Intent();
                    final int apiLevel = Build.VERSION.SDK_INT;
                    if (apiLevel >= 9)
                    { // above 2.3
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", appPackage, null);
                        intent.setData(uri);
                    }
                    else
                    { // below 2.3
                        final String appPkgName = (apiLevel == 8 ? "pkg" : "com.android.settings.ApplicationPkgName");
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.setClassName(ANDROID_SETTINGS_PACKAGE, "com.android.settings.InstalledAppDetails");
                        intent.putExtra(appPkgName, appPackage);
                    }
                    launcher.startActivity(intent);
                }
                catch (Exception e)
                {
                    // failed to tell start app info
                }
			} break;
            case ACTION_MARKET: {
                try
                {
                    try
                    {
                        String appPackage = intent.getComponent().getPackageName();
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("market://search?q=pname:" + appPackage));
                        launcher.startActivity(intent);
                    }
                    catch (Exception e)
                    {
                        // failed to tell market to find the app
                    }
                }
                catch (Exception e)
                {
                    // failed to tell start app info
                }
            } break;
			default: super.executeAction(action, view, launcher);
		}
	}

	@Override
	public List<EditAction> getAvailableActions(View view) {
		List<EditAction> result = super.getAvailableActions(view);
		result.add(new EditAction(ACTION_DELETE,
				android.R.drawable.ic_menu_delete,
				R.string.menu_delete
		));
        // get the application info label and if found show the option
        if (mAppInfoLabel == null)
        {
            try
            {
                Resources resources = view.getContext().createPackageContext(ANDROID_SETTINGS_PACKAGE, Context.CONTEXT_IGNORE_SECURITY).getResources();
                int nameID = resources.getIdentifier("application_info_label", "string", ANDROID_SETTINGS_PACKAGE);
                if (nameID != 0)
                {
                    mAppInfoLabel = resources.getString(nameID);
                }
            }
            catch (Exception e)
            {
                // can't find the settings label
            }
        }
        if (mAppInfoLabel != null)
        {
            result.add(new EditAction(ACTION_APPINFO, 
                    android.R.drawable.ic_menu_info_details, 
                    mAppInfoLabel));
        }

        // get the market icon and label
        if (mMarketIcon == null && mMarketLabel == null)
        {
            try
            {
                PackageManager packageManager = view.getContext().getPackageManager();
                android.content.pm.ApplicationInfo applicationInfo = packageManager.getApplicationInfo(ANDROID_MARKET_PACKAGE, 0);
                mMarketIcon = applicationInfo.loadIcon(packageManager);
                mMarketLabel = applicationInfo.loadLabel(packageManager);
                if (mMarketLabel == null)
                {
                    mMarketLabel = applicationInfo.name;
                }
            }
            catch (Exception e)
            {
                // would appear there is no market
                mMarketIcon = null;
                mMarketLabel = "no-market";
            }
        }

        // if market, show it as an option
        if (mMarketIcon != null && mMarketLabel != null)
        {
            result.add(new EditAction(ACTION_APPINFO, mMarketIcon,mMarketLabel));
        }
		return result;
	}
}

