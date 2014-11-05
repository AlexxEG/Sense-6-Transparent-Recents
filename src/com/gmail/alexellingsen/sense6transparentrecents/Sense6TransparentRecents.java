package com.gmail.alexellingsen.sense6transparentrecents;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.*;
import android.widget.LinearLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Sense6TransparentRecents implements IXposedHookLoadPackage {

    public static final String PACKAGE = "com.android.systemui";
    public static final String TAG = "Sense6TransparentRecents";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(PACKAGE))
            return;

        Class<?> findClass;

        try {
            findClass = XposedHelpers.findClass(
                    PACKAGE + ".recent.RecentAppFxActivity",
                    lpparam.classLoader
            );
        } catch (Throwable e) {
            XposedBridge.log(e);
            return;
        }

        XposedHelpers.findAndHookMethod(
                findClass,
                "onCreate",
                android.os.Bundle.class,

                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity thiz = (Activity) param.thisObject;

                        // Show status bar
                        thiz.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                        // Enable translucent status bar
                        thiz.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

                        // Get status bar height
                        final int statusBarHeight = getStatusBarHeight(thiz.getResources());

                        View actionBarView;

                        // Get the action bar view
                        try {
                            // Try to get 'actionContainer' field. It will throw an NoSuchFieldError if it doesn't exists,
                            // and if it doesn't try an alternative method.
                            actionBarView = (View) XposedHelpers.getObjectField(thiz, "actionContainer");
                        } catch (NoSuchFieldError e) {
                            // Find root layout. ID is from decompiling SystemUI.apk
                            LinearLayout rootLayout = (LinearLayout) thiz.findViewById(0x7f070044);

                            // Get first child view
                            actionBarView = rootLayout.getChildAt(0);
                        }

                        // Insert a new View to color the status bar & move action bar down
                        View statusBarColor = new View(actionBarView.getContext());

                        // Match action bar background, and set minimum height
                        statusBarColor.setBackground(actionBarView.getBackground());
                        statusBarColor.setMinimumHeight(statusBarHeight);

                        // Insert view before action bar into parent
                        ((ViewGroup) actionBarView.getParent()).addView(
                                statusBarColor,
                                0,
                                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBarHeight)
                        );

                        // Check for navigation bar before making any changes.
                        if (hasNavigationBar(thiz)) {
                            // Enable translucent navigation bar
                            thiz.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

                            // Get 3rd child view to fix bottom padding
                            View background = ((ViewGroup) actionBarView.getParent()).getChildAt(2);

                            // Get navigation bar height
                            int navigationBarHeight = getNavigationBarHeight(thiz.getResources());

                            // Increase the bottom padding by navigation bar height
                            background.setPadding(
                                    background.getPaddingLeft(),
                                    background.getPaddingTop(),
                                    background.getPaddingRight(),
                                    background.getPaddingBottom() + navigationBarHeight
                            );
                        }
                    }
                });

        XposedBridge.log("[" + TAG + "] Hooked recent apps activity");
    }

    public int getNavigationBarHeight(Resources resources) {
        int result = 0;
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public int getStatusBarHeight(Resources resources) {
        int result = 0;
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public boolean hasNavigationBar(Context context) {
        boolean hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);

        return (!hasMenuKey && !hasBackKey);
    }
}
