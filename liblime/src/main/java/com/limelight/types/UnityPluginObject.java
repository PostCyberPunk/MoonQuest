package com.limelight.types;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.view.Window;
import android.view.WindowManager;

import com.limelight.LimeLog;
import com.limelight.PluginManager;

import java.io.File;

public abstract class UnityPluginObject {
    protected Activity mActivity;
    protected Intent mIntent;
    protected PluginManager mPluginManager;
    protected boolean isInitialized = false;

    //TODO: why not working?
//    protected UnityPluginObject(Activity activity) {
//        mActivity = activity;
//        this.onCreate();
//    }
//    protected abstract void onCreate();
    protected UnityPluginObject(PluginManager p, Activity a) {
        mPluginManager = p;
        mActivity = a;
    }

    protected UnityPluginObject(PluginManager p, Activity a, Intent i) {
        mPluginManager = p;
        mActivity = a;
        mIntent = i;
    }

    protected abstract void onCreate();

    public abstract void onDestroy();

    public abstract void onResume();

    public abstract void onPause();

    public void onStop() {
    }

    ;

    public boolean IsInitialized() {
        return isInitialized;
    }

    public void Poke() {
    }

    protected void finish() {
        mPluginManager.DestroyPlugin(this);
    }

    //Ported Methods
    protected Intent getIntent() {
        return mIntent;
    }

    protected SharedPreferences getSharedPreferences(String name, int mode) {
        return mActivity.getSharedPreferences(name, mode);
    }

    protected boolean bindService(Intent intent, ServiceConnection conn, int flags) {
        return mActivity.bindService(intent, conn, flags);
    }

    protected void unbindService(ServiceConnection conn) {
        mActivity.unbindService(conn);
    }

    protected File getCacheDir() {
        return mActivity.getCacheDir();
    }

    protected Object getSystemService(String name) {
        return mActivity.getSystemService(name);
    }

    protected Context getApplicationContext() {
        return mActivity.getApplicationContext();
    }

    protected WindowManager getWindowManager() {
        return mActivity.getWindowManager();
    }

    protected Window getWindow() {
        return mActivity.getWindow();
    }

    protected PackageManager getPackageManager() {
        return mActivity.getPackageManager();
    }

    protected Resources getResources() {
        return mActivity.getResources();
    }
}