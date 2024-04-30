package com.pcp.libmoonlight;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.view.Surface;

import java.util.Random;

import android.os.Handler;
import android.os.Looper;

import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import com.unity3d.player.UnityPlayer;

public class StreamPlugin implements SurfaceTexture.OnFrameAvailableListener {

    // Plugin var
    //private int m_state;//0:None 1:Initializing 2:Initialized 3:Streaming
    private enum PluginState {
        None,
        Initializing,
        Initialized,
        Streaming,
    }

    private PluginState mState = PluginState.None;
    // EGL var
    private EGLContext unityContext = EGL14.EGL_NO_CONTEXT;
    private EGLDisplay unityDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLSurface unityDrawSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface unityReadSurface = EGL14.EGL_NO_SURFACE;
    //Stream Params
    private int mTexWidth;
    private int mTexHeight;
    // Surface var
    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int unityTexID;
    private boolean isFrameReady;
    // Test var
    private Rect rec;
    private Paint p;
    private Random rnd;
    private Handler hdl;

    public void Init(int w, int h, int texID) {
        LimeLog.severe(Thread.currentThread().getName() + ": Init");
        mState = PluginState.Initializing;
        mTexWidth = w;
        mTexHeight = h;
//        unityTexID = texID;
        isFrameReady = false;

        if (!initSurface()) {
            LimeLog.severe("Failed to initialize surface");
            return;
        }

        //TODO:Refactor this
        //init test var
        rec = new Rect(0, 0, mTexWidth, mTexHeight);
        p = new Paint();
        rnd = new Random();
        hdl = new Handler(Looper.getMainLooper());
//        UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                LimeLog.severe(Thread.currentThread().getName() + ": Draw");
//                drawRandomCirclesInSurface();
//            }
//        });
        hdl.postDelayed(new Runnable() {
            @Override
            public void run() {
                drawRandomCirclesInSurface();
            }
        }, 100);
        mState = PluginState.Streaming;
    }


    private void drawRandomCirclesInSurface() {
//        LimeLog.severe(Thread.currentThread().getName() + ": Draw");
//        mSurfaceTexture.attachToGLContext(unityTexID);
        Canvas c = mSurface.lockCanvas(rec);
        p.setColor(Color.argb(255, rnd.nextInt(255), rnd.nextInt(255), rnd.nextInt(255)));
        int radius = rnd.nextInt(100);
        c.drawCircle(rnd.nextInt(mTexWidth), rnd.nextInt(mTexHeight), radius, p);
        mSurface.unlockCanvasAndPost(c);
//        LimeLog.info("draw updated");
        hdl.postDelayed(new Runnable() {
            @Override
            public void run() {
                drawRandomCirclesInSurface();
            }
        }, 100);
    }

    private boolean initSurface() {

//        unityContext = EGL14.eglGetCurrentContext();
//        unityDisplay = EGL14.eglGetCurrentDisplay();
//        unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
//        unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

//        if (unityContext == EGL14.EGL_NO_CONTEXT || unityDisplay == EGL14.EGL_NO_DISPLAY || unityDrawSurface == EGL14.EGL_NO_SURFACE || unityReadSurface == EGL14.EGL_NO_SURFACE) {
//            unityContext = EGL14.EGL_NO_CONTEXT;
//            unityDisplay = EGL14.EGL_NO_DISPLAY;
//            unityDrawSurface = EGL14.EGL_NO_SURFACE;
//            unityReadSurface = EGL14.EGL_NO_SURFACE;
//            LimeLog.severe("Failed to get Unity EGL context");
//            return false;
//        }

//        EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext);
        int textures[] = new int[1];
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glGenTextures(1, textures, 0);
        int textureId = textures[0];
        unityTexID = textureId;
        LimeLog.info("Texture ID: " + textureId);
//        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        mSurfaceTexture = new SurfaceTexture(textureId);
        mSurfaceTexture.setDefaultBufferSize(mTexWidth, mTexHeight);
        mSurface = new Surface(mSurfaceTexture);

        mSurfaceTexture.setOnFrameAvailableListener(this);
        mState = PluginState.Initialized;
        return true;
    }

    public void updateSurfaceTexture() {

//        unityContext = EGL14.eglGetCurrentContext();
//        unityDisplay = EGL14.eglGetCurrentDisplay();
//        unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
//        unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
//        EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext);
//        mSurfaceTexture.updateTexImage();
        if (isFrameReady) {
            isFrameReady = false;
            mSurfaceTexture.updateTexImage();
        }
//        else{
//            drawRandomCirclesInSurface();
//        }
    }

    public boolean isInitialized() {
        return mState == PluginState.Streaming;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        isFrameReady = true;
    }

    public int getPluginState() {
        return mState.ordinal();
    }

    //TODO:this is just a placeholder
    public int getTexturePtr() {
        return unityTexID;
    }
}
