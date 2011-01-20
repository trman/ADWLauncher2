/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.List;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentValues;
import android.view.View;
import android.view.ViewGroup;

/**
 * Represents a widget, which just contains an identifier.
 */
class LauncherAppWidgetInfo extends ItemInfo {

    /**
     * Identifier for this widget when talking with
     * {@link android.appwidget.AppWidgetManager} for updates.
     */
    int appWidgetId;

    /**
     * View that holds this widget after it's been created.  This view isn't created
     * until Launcher knows it's needed.
     */
    AppWidgetHostView hostView = null;

    LauncherAppWidgetInfo(int appWidgetId) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        this.appWidgetId = appWidgetId;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);
        values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
    }

    @Override
    public String toString() {
        return "AppWidget(id=" + Integer.toString(appWidgetId) + ")";
    }


    @Override
    void unbind() {
        super.unbind();
        hostView = null;
    }

    private static final int ACTION_DELETE = 1;
    private static final int ACTION_RESIZE = 2;
    private static final int ACTION_UNINSTALL = 3;

	@Override
	public void executeAction(EditAction action, View view, Launcher launcher) {
		switch(action.getId()) {
			case ACTION_DELETE: {
				((ViewGroup) hostView.getParent()).removeView(hostView);
				launcher.removeAppWidget(this);
				LauncherModel.deleteItemFromDatabase(launcher, this);
			} break;
			case ACTION_RESIZE: {
				launcher.editWidget(view);
			} break;
			case ACTION_UNINSTALL: {
				AppWidgetProviderInfo info = hostView.getAppWidgetInfo();
				if (info != null && info.provider != null) {
					launcher.UninstallPackage(info.provider.getPackageName());
				}
			} break;
		}
	}

	@Override
	public List<EditAction> getAvailableActions(View view) {
		List<EditAction> result = super.getAvailableActions(view);
		result.add(new EditAction(ACTION_DELETE,
				android.R.drawable.ic_menu_delete,
				R.string.menu_delete));

		result.add(new EditAction(ACTION_RESIZE,
				android.R.drawable.ic_menu_crop,
				R.string.menu_resize));
		result.add(new EditAction(ACTION_UNINSTALL,
				android.R.drawable.ic_menu_manage,
				R.string.menu_uninstall
		));
		return result;
	}
}
