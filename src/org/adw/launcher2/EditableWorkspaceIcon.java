package org.adw.launcher2;

import java.util.List;

import android.view.View;

public interface EditableWorkspaceIcon {

	class EditAction {
		private final int mIcon;
		private final int mTitle;
		private final int mId;

		public EditAction(int id, int icon, int title) {
			mId = id;
			mIcon = icon;
			mTitle = title;
		}

		public int getIconResourceId() {
			return mIcon;
		}

		public int getTitleResourceId() {
			return mTitle;
		}

		public int getId() {
			return mId;
		}
	}

	public List<EditAction> getAvailableActions(View view);

	public void executeAction(EditAction action, View view, Launcher launcher);
}
