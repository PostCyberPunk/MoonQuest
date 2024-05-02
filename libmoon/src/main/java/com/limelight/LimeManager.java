package com.limelight;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.unity3d.player.UnityPlayer;

public class LimeManager extends Application {
    MyService myService;
    Activity mActivity;
    boolean isBound = false;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyService.LocalBinder binder = (MyService.LocalBinder) service;
            myService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private void Init(Activity tempActivity) {

    }

    public int GetNumber() {
        if (isBound) {
            return myService.getMyInt();
        } else {
//            LimeLog.info("LimeManager Init11");
            System.loadLibrary("moonlight-core");
            UnityMessager.Info("LimeManager Init11");
            mActivity = UnityPlayer.currentActivity;
            Intent intent = new Intent(UnityPlayer.currentActivity, MyService.class);
            isBound = UnityPlayer.currentActivity.bindService(intent, connection, Context.BIND_AUTO_CREATE);
//            UnityPlayer.currentActivity.startForegroundService(intent);
            isBound = true;
            return 0;
        }
    }

}
