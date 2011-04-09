package org.adw.launcher2.quickactionbar;

import org.adw.launcher2.R;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;

public class CustomPopupWindow extends PopupWindow {
	protected final View anchor;
	private View root;
	private Drawable background = null;
	protected final WindowManager windowManager;

	/**
	 * Create a QuickAction
	 *
	 * @param anchor
	 *            the view that the QuickAction will be displaying 'from'
	 */
	public CustomPopupWindow(View anchor) {
		super(anchor.getContext());
		this.anchor = anchor;

		// when a touch even happens outside of the window
		// make the window go away
		setTouchInterceptor(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
					CustomPopupWindow.this.dismiss();

					return true;
				}

				return false;
			}
		});

		windowManager = (WindowManager) anchor.getContext().getSystemService(Context.WINDOW_SERVICE);

		onCreate();
	}

	/**
	 * Anything you want to have happen when created. Probably should create a view and setup the event listeners on
	 * child views.
	 */
	protected void onCreate() {}

	/**
	 * In case there is stuff to do right before displaying.
	 */
	protected void onShow() {}

	protected void preShow() {
		if (root == null) {
			throw new IllegalStateException("setContentView was not called with a view to display.");
		}

		onShow();

		if (background == null) {
			setBackgroundDrawable(new BitmapDrawable());
		} else {
			setBackgroundDrawable(background);
		}

		// if using PopupWindow#setBackgroundDrawable this is the only values of the width and hight that make it work
		// otherwise you need to set the background of the root viewgroup
		// and set the popupwindow background to an empty BitmapDrawable

		setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
		setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
		setTouchable(true);
		setFocusable(true);
		setOutsideTouchable(true);

		setContentView(root);
	}

	@Override
	public void setBackgroundDrawable(Drawable background) {
		this.background = background;
	}

	/**
	 * Sets the content view. Probably should be called from {@link onCreate}
	 *
	 * @param root
	 *            the view the popup will display
	 */
	@Override
	public void setContentView(View root) {
		this.root = root;

		super.setContentView(root);
	}

	/**
	 * Will inflate and set the view from a resource id
	 *
	 * @param layoutResID
	 */
	public void setContentView(int layoutResID) {
		LayoutInflater inflator =
				(LayoutInflater) anchor.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		setContentView(inflator.inflate(layoutResID, null));
	}

	/**
	 * Displays like a popdown menu from the anchor view
	 */
	public void showDropDown() {
		showDropDown(0, 0);
	}

	/**
	 * Displays like a popdown menu from the anchor view.
	 *
	 * @param xOffset
	 *            offset in X direction
	 * @param yOffset
	 *            offset in Y direction
	 */
	public void showDropDown(int xOffset, int yOffset) {
		preShow();

		setAnimationStyle(R.style.Animations_PopDownMenu);

		showAsDropDown(anchor, xOffset, yOffset);
	}

	/**
	 * Displays like a QuickAction from the anchor view.
	 */
	public void showLikeQuickAction() {
		showLikeQuickAction(0, 0);
	}

	/**
	 * Displays like a QuickAction from the anchor view.
	 *
	 * @param xOffset
	 *            offset in the X direction
	 * @param yOffset
	 *            offset in the Y direction
	 */
	public void showLikeQuickAction(int xOffset, int yOffset) {
		preShow();

		setAnimationStyle(R.style.Animations_PopUpMenu_Center);

		int[] location = new int[2];
		anchor.getLocationOnScreen(location);

		Rect anchorRect =
				new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1]
					+ anchor.getHeight());

		root.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		root.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

		int rootWidth 		= root.getMeasuredWidth();
		int rootHeight 		= root.getMeasuredHeight();

		int screenWidth 	= windowManager.getDefaultDisplay().getWidth();
		//int screenHeight 	= windowManager.getDefaultDisplay().getHeight();

		int xPos 			= ((screenWidth - rootWidth) / 2) + xOffset;
		int yPos	 		= anchorRect.top - rootHeight + yOffset;

		// display on bottom
		if (rootHeight > anchorRect.top) {
			yPos = anchorRect.bottom + yOffset;

			setAnimationStyle(R.style.Animations_PopDownMenu_Center);
		}

		showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
	}
}