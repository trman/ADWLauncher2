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

import java.util.List;

import android.view.View;


/**
 * Represents a folder containing shortcuts or apps.
 */
class FolderInfo extends ItemInfo {

    /**
     * Whether this folder has been opened
     */
    boolean opened;

    /**
     * The folder name.
     */
    CharSequence title;

    private static final int ACTION_DELETE = 1;

    @Override
	public void executeAction(EditAction action, View view, Launcher launcher) {
		switch(action.getId()) {
			case ACTION_DELETE: {
				launcher.removeDesktopItem(this);
				LauncherModel.deleteItemFromDatabase(launcher, this);
			} break;
		}
	}

	@Override
	public List<EditAction> getAvailableActions(View view) {
		List<EditAction> result = super.getAvailableActions(view);
		result.add(new EditAction(ACTION_DELETE,
				android.R.drawable.ic_menu_delete,
				R.string.menu_delete));
		return result;
	}
}
