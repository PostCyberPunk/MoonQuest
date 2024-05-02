package com.limelight.types;

import com.limelight.nvstream.http.NvApp;

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
}
