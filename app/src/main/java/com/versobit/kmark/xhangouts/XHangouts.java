/*
 * Copyright (C) 2014-2016 Kevin Mark
 *
 * This file is part of XHangouts.
 *
 * XHangouts is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * XHangouts is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with XHangouts.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.versobit.kmark.xhangouts;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.versobit.kmark.xhangouts.mods.ImageCompression;
import com.versobit.kmark.xhangouts.mods.ImageResizing;
import com.versobit.kmark.xhangouts.mods.MmsResizing;
import com.versobit.kmark.xhangouts.mods.Sound;
import com.versobit.kmark.xhangouts.mods.UiCallButtons;
import com.versobit.kmark.xhangouts.mods.UiColorize;
import com.versobit.kmark.xhangouts.mods.UiDisableProximity;
import com.versobit.kmark.xhangouts.mods.UiEmoji;
import com.versobit.kmark.xhangouts.mods.UiEnterKey;
import com.versobit.kmark.xhangouts.mods.UiMsgTypeSpinner;
import com.versobit.kmark.xhangouts.mods.UiQuickSettings;
import com.versobit.kmark.xhangouts.mods.UiSendLock;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public final class XHangouts implements IXposedHookZygoteInit,
        IXposedHookLoadPackage, IXposedHookInitPackageResources {

    private static final String TAG = XHangouts.class.getSimpleName();

    private static final String ACTIVITY_THREAD_CLASS = "android.app.ActivityThread";
    private static final String ACTIVITY_THREAD_CURRENTACTHREAD = "currentActivityThread";
    private static final String ACTIVITY_THREAD_GETSYSCTX = "getSystemContext";

    private static final Class ACTIVITY_THREAD = findClass(ACTIVITY_THREAD_CLASS, null);

    public static final String HANGOUTS_PKG_NAME = "com.google.android.talk";
    public static final String HANGOUTS_RES_PKG_NAME = "com.google.android.apps.hangouts";

    private static final String TESTED_VERSION_STR = "8.1.121732641";
    private static final int MIN_VERSION_INT = 23142322;
    private static final int MAX_VERSION_INT = 23142355;

    private static final Config config = new Config();

    public static String MODULE_PATH = null;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;

        UiColorize.initZygote();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(loadPackageParam.packageName)) {
            // Passing in just XApp.class does not work :(
            findAndHookMethod(XApp.class.getName(), loadPackageParam.classLoader, "isActive",
                    XC_MethodReplacement.returnConstant(true));
            return;
        }

        if (!loadPackageParam.packageName.equals(HANGOUTS_PKG_NAME)) {
            return;
        }

        Object activityThread = callStaticMethod(ACTIVITY_THREAD, ACTIVITY_THREAD_CURRENTACTHREAD);
        Context systemCtx = (Context) callMethod(activityThread, ACTIVITY_THREAD_GETSYSCTX);

        config.reload(systemCtx);

        debug("--- LOADING XHANGOUTS ---", false);
        debug(String.format("XHangouts v%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), false);

        final PackageInfo pi = systemCtx.getPackageManager().getPackageInfo(HANGOUTS_PKG_NAME, 0);
        debug(String.format("Google Hangouts v%s (%d)", pi.versionName, pi.versionCode), false);

        // Do not warn unless Hangouts version is > +/- the VERSION_TOLERANCE of the supported version
        if (!versionSupported(pi.versionCode)) {
            log(String.format("Warning: Your Hangouts version significantly differs from the version XHangouts was built against: v%s (%d)",
                    TESTED_VERSION_STR, MIN_VERSION_INT), false);
        }

        ImageCompression.handleLoadPackage(config, loadPackageParam.classLoader);
        ImageResizing.handleLoadPackage(config, loadPackageParam.classLoader);
        //MmsApnSplicing.handleLoadPackage(config, loadPackageParam.classLoader);
        MmsResizing.handleLoadPackage(config, loadPackageParam.classLoader);
        Sound.handleLoadPackage(config);
        UiCallButtons.handleLoadPackage(config, loadPackageParam.classLoader);
        UiColorize.handleLoadPackage(config, loadPackageParam.classLoader);
        UiDisableProximity.handleLoadPackage(config);
        UiEmoji.handleLoadPackage(config, loadPackageParam.classLoader);
        UiEnterKey.handleLoadPackage(config, loadPackageParam.classLoader);
        UiMsgTypeSpinner.handleLoadPackage(config, loadPackageParam.classLoader);
        UiQuickSettings.handleLoadPackage(config, loadPackageParam.classLoader);
        UiSendLock.handleLoadPackage(config, loadPackageParam.classLoader);

        debug("--- XHANGOUTS LOAD COMPLETE ---", false);
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam initPackageResourcesParam) throws Throwable {
        if (!config.modEnabled) {
            return;
        }

        if (!initPackageResourcesParam.packageName.equals(HANGOUTS_PKG_NAME)) {
            return;
        }

        UiColorize.handleInitPackageResources(config, initPackageResourcesParam.res);
        UiQuickSettings.handleInitPackageResources(initPackageResourcesParam.res);
    }

    public static boolean versionSupported(int vCode) {
        return vCode >= MIN_VERSION_INT && vCode <= MAX_VERSION_INT;
    }

    public static void debug(String msg) {
        debug(msg, true);
    }

    private static void debug(String msg, boolean useTag) {
        if (config.debug) {
            log(msg, useTag);
        }
    }

    public static void log(String msg) {
        log(msg, true);
    }

    static void log(String msg, boolean useTag) {
        XposedBridge.log((useTag ? TAG + ": " : "") + msg);
    }

    public static void log(Throwable throwable) {
        XposedBridge.log(throwable);
    }
}
