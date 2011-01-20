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
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
class ShortcutInfo extends ItemInfo implements EditableWorkspaceIcon {

    /**
     * The application name.
     */
    private CharSequence mTitle;

    /**
     * The intent used to start the application.
     */
    Intent intent;

    /**
     * The application icon.
     */
    private Bitmap mIcon = null;

    ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    public ShortcutInfo(ShortcutInfo info) {
        super(info);
        mTitle = info.mTitle;
        intent = new Intent(info.intent);
        mIcon = info.mIcon; // TODO: should make a copy here.  maybe we don't need this ctor at all
    }

    public ShortcutInfo(ComponentName componentName) {
    	this.container = ItemInfo.NO_ID;
    	this.mTitle = null;
    	this.setActivity(componentName, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    public void setIcon(Bitmap b) {
        mIcon = b;
    }

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

        String titleStr = mTitle != null ? mTitle.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        String uri = intent != null ? intent.toUri(0) : null;
        values.put(LauncherSettings.BaseLauncherColumns.INTENT, uri);

        if (mIcon == null)
        	values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
        			LauncherSettings.BaseLauncherColumns.ICON_TYPE_RESOURCE);
        else {
        	values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
        			LauncherSettings.BaseLauncherColumns.ICON_TYPE_BITMAP);
        	writeBitmap(values, mIcon);
        }
    }

    @Override
    public String toString() {
        return "ShortcutInfo(title=" + mTitle + ")";
    }

    @Override
    void unbind() {
        super.unbind();
    }

    public CharSequence getTitle(IconCache mIconCache) {
    	if (mTitle == null)
    		return mIconCache.getTitle(this.intent);
    	return mTitle;
    }

    public void setTitle(CharSequence value) {
    	mTitle = value;
    }

    public static void dumpShortcutInfoList(String tag, String label,
            ArrayList<ShortcutInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (ShortcutInfo info: list) {
            Log.d(tag, "   title=\"" + info.mTitle + " icon=" + info.mIcon);
        }
    }

	private static final int ACTION_DELETE = 1;
	private static final int ACTION_UNINSTALL = 2;
	private static final int COUNT_ACTIONS = 2;

	@Override
	public void executeAction(EditAction action, View view, Launcher launcher) {
		switch(action.getId()) {
			case ACTION_DELETE: {
				launcher.removeDesktopItem(this);
				LauncherModel.deleteItemFromDatabase(launcher, this);
			} break;
			case ACTION_UNINSTALL: {
				launcher.UninstallPackage(launcher.getPackageNameFromIntent(intent));
			} break;
		}

	}

	@Override
	public List<EditAction> getAvailableActions(View view) {
		List<EditAction> result = new ArrayList<EditAction>(COUNT_ACTIONS);
		result.add(new EditAction(ACTION_DELETE,
				android.R.drawable.ic_menu_delete,
				R.string.menu_delete
		));
		result.add(new EditAction(ACTION_UNINSTALL,
				android.R.drawable.ic_menu_manage,
				R.string.menu_uninstall
		));

		return result;
	}
}

