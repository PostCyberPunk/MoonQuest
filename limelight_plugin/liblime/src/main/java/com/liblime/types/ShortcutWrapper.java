package com.liblime.types;

import java.io.Serializable;

//TODO: in case there is illegal symbol in appname,
//we need wrap all the intent any way
public class ShortcutWrapper implements Serializable {
    public String uuid;
    public String appName;
    public int appID;

    public ShortcutWrapper(String uuid, String appName, int appID) {
        this.uuid = uuid;
        this.appName = appName;
        this.appID = appID;
    }
}
