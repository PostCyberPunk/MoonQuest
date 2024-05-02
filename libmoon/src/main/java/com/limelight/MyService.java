package com.limelight;


import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;


import java.util.Timer;
import java.util.TimerTask;

public class MyService extends Service {
    private final IBinder binder = new LocalBinder();
    private int myInt = 0;
    private Timer timer;

    public class LocalBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        LimeLog.info("MyService onBind");
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                myInt++;
            }
        }, 0, 1000);
        return binder;
    }

    public int getMyInt() {
        return myInt;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }
    @Override
    public boolean onUnbind(Intent intent) {
        LimeLog.info("MyService onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        LimeLog.info("MyService onCreate");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LimeLog.info("onStartCommand: STARTED");
        super.onCreate();
        return START_NOT_STICKY;
    }

}
