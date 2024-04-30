package com.pcp.libmoonlight;

import android.util.Log;

public class LimeLog {
    private static final String LogTag = "com.pcp.libmoonlight";

    public static void info(String msg) {
        Log.i(LogTag, msg);
    }

    public static void warning(String msg) {
        Log.w(LogTag, msg);
    }

    public static void severe(String msg) {
        Log.e(LogTag, msg);
    }
}
