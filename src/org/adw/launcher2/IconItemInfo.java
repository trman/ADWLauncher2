package org.adw.launcher2;

import java.util.List;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;

public abstract class IconItemInfo extends ItemInfo{

    /**
     * The application name.
     */
    protected CharSequence mTitle;

    /**
     * The application icon.
     */
    protected Bitmap mIcon = null;

    IconItemInfo() {
    }

	public IconItemInfo(IconItemInfo info) {
		super(info);
		mIcon = info.mIcon;
		mTitle = info.mTitle;
	}


    public void setIcon(Bitmap b) {
        mIcon = b;
    }

    public Bitmap getIcon(IconCache iconCache) {
        if (mIcon == null) {
            return iconCache.getDefaultIcon();
        }
        return mIcon;
    }

    public boolean usesDefaultIcon() {
    	return mIcon == null;
    }

	public CharSequence getTitle(IconCache mIconCache)
	{
		if (mTitle == null)
			return "";
		return mTitle;
	}

    public void setTitle(CharSequence value) {
    	mTitle = value;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String titleStr = mTitle != null ? mTitle.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        if (mIcon == null)
        	values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
        			LauncherSettings.BaseLauncherColumns.ICON_TYPE_RESOURCE);
        else {
        	values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
        			LauncherSettings.BaseLauncherColumns.ICON_TYPE_BITMAP);
        	writeBitmap(values, mIcon);
        }
    }

	private static final int ACTION_EDIT = 100;

	@Override
	public void executeAction(EditAction action, View view, Launcher launcher) {
	    if (action.getId() == ACTION_EDIT) {
			Intent edit = new Intent(Intent.ACTION_EDIT);
			edit.setClass(launcher, CustomShirtcutActivity.class);
			edit.putExtra(CustomShirtcutActivity.EXTRA_APPLICATIONINFO, id);
			launcher.startActivityForResult(edit, Launcher.REQUEST_EDIT_SHIRTCUT);
	    }
	}

	@Override
	public List<EditAction> getAvailableActions(View view) {
		List<EditAction> result = super.getAvailableActions(view);
		result.add(new EditAction(ACTION_EDIT,
				android.R.drawable.ic_menu_edit,
				R.string.menu_edit));
		return result;
	}
}
