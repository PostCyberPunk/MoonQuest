package com.liblime;

import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.liblime.R;
import com.liblime.types.UnityPluginObject;
import com.unity3d.player.UnityPlayer;

import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private AppPlugin m_AppPlugin;
    private PcPlugin m_PcPlugin;
    private StreamPlugin m_StreamPlugin;
    private final List<UnityPluginObject> m_PluginList = new ArrayList<>();
    private Activity mActivity;
    public boolean IsInitialized() {
        return true;
    }

    public void Init() {
        //TRY
        mActivity = UnityPlayer.currentActivity;
        PreferenceManager.setDefaultValues(mActivity, R.xml.preferences, false);
        ActivatePcPlugin();
    }

    //Lifecycle-----------
    public void Destroy() {
        for (UnityPluginObject plugin : m_PluginList) {
            if (plugin != null)
                plugin.onDestroy();
        }
    }

    public void onResume() {
        for (UnityPluginObject plugin : m_PluginList) {
            if (plugin != null)
                plugin.onResume();
        }
    }

    public void onPause() {
        for (UnityPluginObject plugin : m_PluginList) {
            if (plugin != null)
                plugin.onPause();
        }
    }

    public void onStop() {
        for (UnityPluginObject plugin : m_PluginList) {
            if (plugin != null)
                plugin.onStop();
        }
    }

    //plugins-----------
    //Thats a fucking mess, create a hashmap
    //TODO should sealed activating methods
    private void ActivatePcPlugin() {
        LimeLog.info("PcPlugin starting");
        m_PcPlugin = new PcPlugin(this, mActivity);
        m_PluginList.add(m_PcPlugin);
    }

    public void StopPcPlugin() {
        if (m_PcPlugin == null)
            return;
        m_PluginList.remove(m_PcPlugin);
        m_PcPlugin.onDestroy();
        m_PcPlugin = null;
        LimeLog.info("PcPlugin stopped");
    }

    public void ActivateAppPlugin(Intent i) {
        LimeLog.info("AppPlugin starting");
        m_AppPlugin = new AppPlugin(this, mActivity, i);
        m_PluginList.add(m_AppPlugin);
    }

    public void StopAppPlugin() {
        if (m_AppPlugin == null)
            return;
        m_PluginList.remove(m_AppPlugin);
        m_AppPlugin.onDestroy();
        m_AppPlugin = null;
        LimeLog.info("AppPlugin stopped");
    }

    public void ActivateStreamPlugin(Intent i) {
        LimeLog.info("StreamPlugin starting");
        m_StreamPlugin = new StreamPlugin(this, mActivity, i);
        m_PluginList.add(m_StreamPlugin);
    }

    public void StopStreamPlugin() {
        if (m_StreamPlugin == null)
            return;
        m_PluginList.remove(m_StreamPlugin);
        m_StreamPlugin.onDestroy();
        m_StreamPlugin = null;
        LimeLog.info("StreamPlugin stopped");
    }

    public void DestroyPlugin(UnityPluginObject plugin) {
        if (plugin == null)
            return;
        //call stop method base on the plugin types
        if (plugin instanceof AppPlugin) {
            StopAppPlugin();
        } else if (plugin instanceof PcPlugin) {
            StopPcPlugin();
        } else if (plugin instanceof StreamPlugin) {
            StopStreamPlugin();
        }
    }

    //Methods-----------
    public void Poke() {
        LimeLog.info("PluginManager Poked");
    }

    public Activity GetActivity() {
        return mActivity;
    }

    public void FakeStart() {
        mActivity.runOnUiThread(() -> {
            m_PcPlugin.fakeStart();
        });
    }

    public StreamPlugin GetStreamPlugin() {
        return m_StreamPlugin;
    }
}
