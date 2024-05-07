package com.liblime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.liblime.types.UnityPluginObject;
import com.unity3d.player.UnityPlayer;

import java.util.EnumMap;

public class PluginManager {
    private Activity mActivity;
    @SuppressLint("StaticFieldLeak")
    private static PluginManager m_Instance;

    public static PluginManager GetInstance() {
        if (m_Instance == null)
            LimeLog.severe("PluginManager is null!");
        return m_Instance;
    }

    public enum PluginType {
        PC,
        APP,
        STREAM
    }

    private EnumMap<PluginType, UnityPluginObject> m_PluginMap;


    public void Init() {
        mActivity = UnityPlayer.currentActivity;
        PreferenceManager.setDefaultValues(mActivity, R.xml.preferences, false);
        //Try
        fakeSetup();

        m_Instance = this;
        m_PluginMap = new EnumMap<>(PluginType.class);
        LimeLog.debug("PluginManager Initialized");
        //TRY
//        ActivatePlugin(PluginType.PC, null);
    }

    //Lifecycle-----------

    public void onResume() {
        for (UnityPluginObject plugin : m_PluginMap.values()) {
            if (plugin != null)
                plugin.onResume();
        }
    }

    public void onPause() {
        for (UnityPluginObject plugin : m_PluginMap.values()) {
            if (plugin != null)
                plugin.onPause();
        }
    }

    public void Destroy() {
        DestroyAllPlugins();
        m_Instance = null;
        mActivity = null;
    }

    //plugins-----------
    public void ActivatePlugin(PluginType pluginType, Intent i) {
        if (m_PluginMap.get(pluginType) != null) {
            DeActivePlugin(pluginType);
            LimeLog.warning("Duplicated " + pluginType + " found,create a new one");
        }
        LimeLog.debug("Plugin " + pluginType + ":Activating");
        if (pluginType == PluginType.PC) {
            m_PluginMap.put(pluginType, new PcPlugin(this, mActivity));
        } else if (pluginType == PluginType.APP) {
            m_PluginMap.put(pluginType, new AppPlugin(this, mActivity, i));
        } else if (pluginType == PluginType.STREAM) {
            m_PluginMap.put(pluginType, new StreamPlugin(this, mActivity, i));
        }
    }

    public void DeActivePlugin(PluginType t) {
        var plugin = m_PluginMap.get(t);
        if (plugin != null) {
            LimeLog.debug("Plugin " + t + ":Deactivating");
            plugin.onDestroy();
        }
    }

    public void RemovePlugin(PluginType t) {
        m_PluginMap.remove(t);
    }

    public void DestroyAllPlugins() {
        LimeLog.debug("Destroying all plugins");
        for (UnityPluginObject plugin : m_PluginMap.values()) {
            if (plugin != null)
                plugin.onDestroy();
        }
        m_PluginMap.clear();
    }

    public UnityPluginObject GetPlugin(PluginType t) {
        return m_PluginMap.get(t);
    }

    public UnityPluginObject GetPlugin(int t) {
        return GetPlugin(PluginType.values()[t]);
    }

    public boolean HasRunningPlugin() {
        return !m_PluginMap.isEmpty();
    }

    //Methods-----------
    public void Poke() {
        LimeLog.verbose("PluginManager Poked");
    }

    public Activity GetActivity() {
        return mActivity;
    }

    //TRY
    public void StartPC() {
        ActivatePlugin(PluginType.PC, null);
    }

    private void fakeSetup() {
        var editor = PreferenceManager.getDefaultSharedPreferences(mActivity).edit();
        editor.putString("list_resolution", "3440x1440");
//        editor.putInt("seekbar_bitrate_kbps", 50000);
//        editor.putBoolean("checkbox_host_audio", true);
//        editor.putBoolean("checkbox_enable_sops", false);
        editor.apply();
    }
    //END OF CLASS
}
