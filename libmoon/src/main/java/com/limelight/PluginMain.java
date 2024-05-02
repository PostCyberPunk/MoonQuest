package com.limelight;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.RelativeLayout;

import com.limelight.R;

import com.limelight.types.UnityPluginObject;
import com.tlab.viewtohardwarebuffer.CustomGLSurfaceView;
import com.tlab.viewtohardwarebuffer.GLLinearLayout;

import java.util.ArrayList;
import java.util.List;

public class PluginMain {
    private AppPlugin m_AppPlugin;
    private PcPlugin m_PcPlugin;
    private Game m_Game;
    private final List<UnityPluginObject> m_PluginList = new ArrayList<>();
    private Activity mActivity;
    public GLLinearLayout mLayout;

    public PluginMain(Activity a, GLLinearLayout l) {
        //TRY
        mActivity = a;
        mLayout = l;

        PreferenceManager.setDefaultValues(mActivity, R.xml.preferences, false);
        ActivatePcPlugin();
    }

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
    public void ActivatePcPlugin() {
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

    public void ActivateGamePlugin(Intent i) {
        LimeLog.info("GamePlugin starting");
        m_Game = new Game(this, mActivity, i);
        m_PluginList.add(m_Game);
    }

    public void StopGamePlugin() {
        if (m_Game == null)
            return;
        m_PluginList.remove(m_Game);
        m_Game.onDestroy();
        m_Game = null;
        LimeLog.info("GamePlugin stopped");
    }

    public void DestroyPlugin(UnityPluginObject plugin) {
        if (plugin == null)
            return;
        //call stop method base on the plugin types
        if (plugin instanceof AppPlugin) {
            StopAppPlugin();
        } else if (plugin instanceof PcPlugin) {
            StopPcPlugin();
        } else if (plugin instanceof Game) {
            StopGamePlugin();
        }
    }

    public Activity GetActivity() {
        return mActivity;
    }

    public void fakeStart() {
       m_PcPlugin.fakeStart();
    }
}
