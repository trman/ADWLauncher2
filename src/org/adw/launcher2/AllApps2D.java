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
import java.util.Collections;

import org.adw.launcher2.settings.Preferences;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class AllApps2D
        extends RelativeLayout
        implements AllAppsView,
                   AdapterView.OnItemClickListener,
                   AdapterView.OnItemLongClickListener,
                   View.OnKeyListener,
                   DragSource {

    private static final String TAG = "Launcher.AllApps2D";
    private static final boolean DEBUG = false;

    private Launcher mLauncher;
    private DragController mDragController;

    private GridView mGrid;

    private final ArrayList<ShortcutInfo> mAllAppsList = new ArrayList<ShortcutInfo>();

    // preserve compatibility with 3D all apps:
    //    0.0 -> hidden
    //    1.0 -> shown and opaque
    //    intermediate values -> partially shown & partially opaque
    private float mZoom;

    private final AppsAdapter mAppsAdapter;

    // ------------------------------------------------------------

    public static class HomeButton extends ImageButton {
        public HomeButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        @Override
        public View focusSearch(int direction) {
            if (direction == FOCUS_UP) return super.focusSearch(direction);
            return null;
        }
    }

    public class AppsAdapter extends ArrayAdapter<ShortcutInfo> {
        private final LayoutInflater mInflater;

        public AppsAdapter(Context context, ArrayList<ShortcutInfo> apps) {
            super(context, 0, apps);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ShortcutInfo info = getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.application_boxed, parent, false);
            }

//            if (!info.filtered) {
//                info.icon = Utilities.createIconThumbnail(info.icon, getContext());
//                info.filtered = true;
//            }

            final TextView textView = (TextView) convertView;
            Bitmap icon = info.getIcon(mLauncher.getIconCache());
            if (DEBUG) {
                Log.d(TAG, "icon bitmap = " + icon
                    + " density = " + icon.getDensity());
            }
            icon.setDensity(Bitmap.DENSITY_NONE);
            textView.setCompoundDrawablesWithIntrinsicBounds(null, new BitmapDrawable(icon), null, null);
            textView.setText(info.getTitle(mLauncher.getIconCache()));

            return convertView;
        }
    }

    public AllApps2D(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(View.GONE);
        setSoundEffectsEnabled(false);

        mAppsAdapter = new AppsAdapter(getContext(), mAllAppsList);
        mAppsAdapter.setNotifyOnChange(false);
    }

    @Override
    protected void onFinishInflate() {
        setBackgroundColor(Color.BLACK);

        try {
            mGrid = (GridView)findViewWithTag("all_apps_2d_grid");
            if (mGrid == null) throw new Resources.NotFoundException();
            mGrid.setOnItemClickListener(this);
            mGrid.setOnItemLongClickListener(this);
            mGrid.setBackgroundColor(Color.BLACK);
            mGrid.setCacheColorHint(Color.BLACK);

            ImageButton homeButton = (ImageButton) findViewWithTag("all_apps_2d_home");
            if (homeButton == null) throw new Resources.NotFoundException();
            homeButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        mLauncher.closeAllApps(true);
                    }
                });
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllApps2D");
        }

        setOnKeyListener(this);
    }

    public AllApps2D(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!isVisible()) return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                mLauncher.closeAllApps(true);
                break;
            default:
                return false;
        }

        return true;
    }

    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
    	ShortcutInfo app = (ShortcutInfo) parent.getItemAtPosition(position);
        mLauncher.startActivitySafely(app.intent, app);
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (!view.isInTouchMode()) {
            return false;
        }

        ShortcutInfo app = (ShortcutInfo) parent.getItemAtPosition(position);
        app = new ShortcutInfo(app);

        mDragController.startDrag(view, this, app, DragController.DRAG_ACTION_COPY);
        mLauncher.closeAllApps(true);

        return true;
    }

    @Override
	protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect prev) {
        if (gainFocus) {
            mGrid.requestFocus();
        }
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void onDropCompleted(View target, boolean success) {
    }

    /**
     * Zoom to the specifed level.
     *
     * @param zoom [0..1] 0 is hidden, 1 is open
     */
    public void zoom(float zoom, boolean animate) {
//        Log.d(TAG, "zooming " + ((zoom == 1.0) ? "open" : "closed"));
        cancelLongPress();

        mZoom = zoom;

        if (isVisible()) {
            getParent().bringChildToFront(this);
            setVisibility(View.VISIBLE);
            mGrid.setAdapter(mAppsAdapter);
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_in));
            } else {
                onAnimationEnd();
            }
        } else {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_out));
            } else {
                onAnimationEnd();
            }
        }
    }

    @Override
	protected void onAnimationEnd() {
        if (!isVisible()) {
            setVisibility(View.GONE);
            mGrid.setAdapter(null);
            mZoom = 0.0f;
        } else {
            mZoom = 1.0f;
        }

        mLauncher.zoomed(mZoom);
    }

    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    @Override
    public boolean isOpaque() {
        return mZoom > 0.999f;
    }

    public void setApps(ArrayList<ShortcutInfo> list) {
        mAllAppsList.clear();
        addApps(list);
    }

    public void addApps(ArrayList<ShortcutInfo> list) {
//        Log.d(TAG, "addApps: " + list.size() + " apps: " + list.toString());

        final int N = list.size();

        for (int i=0; i<N; i++) {
            final ShortcutInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item, Preferences.getInstance().getCurrentDrawerComparator());
            if (index < 0) {
                index = -(index+1);
            }
            mAllAppsList.add(index, item);
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void sort() {
    	Collections.sort(mAllAppsList, Preferences.getInstance().getCurrentDrawerComparator());
    	mAppsAdapter.notifyDataSetChanged();
    }

    public void removeApps(ArrayList<ShortcutInfo> list) {
        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ShortcutInfo item = list.get(i);
            int index = findAppByComponent(mAllAppsList, item);
            if (index >= 0) {
                mAllAppsList.remove(index);
            } else {
                Log.w(TAG, "couldn't find a match for item \"" + item + "\"");
                // Try to recover.  This should keep us from crashing for now.
            }
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void updateApps(ArrayList<ShortcutInfo> list) {
        // Just remove and add, because they may need to be re-sorted.
        removeApps(list);
        addApps(list);
    }

    private static int findAppByComponent(ArrayList<ShortcutInfo> list, ShortcutInfo item) {
        ComponentName component = item.intent.getComponent();
        if (component == null)
        	return -1;
        final int N = list.size();
        for (int i=0; i<N; i++) {
            ShortcutInfo x = list.get(i);
            if (component.equals(x.intent.getComponent())) {
                return i;
            }
        }
        return -1;
    }

    public void dumpState() {
    	ShortcutInfo.dumpShortcutInfoList(TAG, "mAllAppsList", mAllAppsList);
    }

    public void surrender() {
    }
}


