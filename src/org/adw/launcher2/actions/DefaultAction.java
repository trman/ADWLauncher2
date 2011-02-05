package org.adw.launcher2.actions;

import java.util.List;

import org.adw.launcher2.Launcher;
import org.adw.launcher2.R;

import android.content.Intent;

public class DefaultAction implements LauncherActions.Action {

	private static final String EXTRA_BINDINGVALUE = "DefaultLauncherAction.EXTRA_BINDINGVALUE";

	private static final int ACTION_OPENCLOSE_DRAWER = 1;

	private final int mBindingValue;
	private final String mName;

	public static void GetActions(List<LauncherActions.Action> list) {
		Launcher launcher = LauncherActions.getInstance().getLauncher();
		if (launcher == null)
			return;
		list.add(new DefaultAction(ACTION_OPENCLOSE_DRAWER, launcher.getString(R.string.action_opendrawer)));
	}

	public DefaultAction(int bindingValue, String name) {
		mBindingValue = bindingValue;
		mName = name;
	}

	@Override
	public String getName() {
		return mName;
	}

	@Override
	public void putIntentExtras(Intent intent) {
		intent.putExtra(EXTRA_BINDINGVALUE, mBindingValue);
	}

	@Override
	public boolean runIntent(Intent intent) {
		if (intent.hasExtra(EXTRA_BINDINGVALUE))
		{
			int val = intent.getIntExtra(EXTRA_BINDINGVALUE, 0);
			if (val == mBindingValue) {
				fireHomeBinding(mBindingValue);
				return true;
			}

		}

		return false;
	}

	@Override
	public int getIconResourceId() {
		switch(mBindingValue) {
			default:
				return R.drawable.ic_launcher_home;
		}
	}

	private static void fireHomeBinding(int value) {
		Launcher launcher = LauncherActions.getInstance().getLauncher();
		if (launcher == null)
			return;
		switch(value) {
			case ACTION_OPENCLOSE_DRAWER: {
				if (launcher.isAllAppsVisible())
					launcher.closeAllApps(true);
				else
					launcher.showAllApps(true);
			} break;
		}
	}
}
