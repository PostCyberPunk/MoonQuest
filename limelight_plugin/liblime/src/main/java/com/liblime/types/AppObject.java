package com.liblime.types;

import com.google.gson.Gson;
import com.limelight.nvstream.http.NvApp;

import java.io.Serializable;

public class AppObject {
    public final NvApp app;
    public boolean isRunning;

    public AppObject(NvApp app) {
        if (app == null) {
            throw new IllegalArgumentException("app must not be null");
        }
        this.app = app;
    }

    @Override
    public String toString() {
        return app.getAppName();
    }

    public AppData ToData() {
        return new AppData(app);
    }

    public static class AppData implements Serializable {
        public String appName;
        public int appId = -1;
        public boolean initialized = false;

        public AppData(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.appName = app.getAppName();
            this.appId = app.getAppId();
            this.initialized = app.isInitialized();
        }
    }
}
