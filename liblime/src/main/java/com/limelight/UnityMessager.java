package com.limelight;

import android.app.Activity;

import com.unity3d.player.UnityPlayer;

public class UnityMessager {
    public static void Info(String msg) {
        UnityPlayer.UnitySendMessage("MessageManager", "Info", msg);
    }

    public static void Warn(String msg) {
        UnityPlayer.UnitySendMessage("MessageManager", "Warn", msg);
    }

    public static void Error(String msg) {
        UnityPlayer.UnitySendMessage("MessageManager", "Error", msg);
    }
}
