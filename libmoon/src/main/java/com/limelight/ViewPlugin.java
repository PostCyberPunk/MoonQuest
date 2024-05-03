package com.limelight;

import android.app.Activity;
import android.graphics.Bitmap;
//import android.app.Fragment;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.HardwareBuffer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES30;
//import android.os.Build;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

//import androidx.annotation.RequiresApi;

import com.limelight.types.UnityPluginObject;
import com.robot9.shared.SharedTexture;
import com.tlab.viewtohardwarebuffer.CustomGLSurfaceView;
import com.tlab.viewtohardwarebuffer.GLLinearLayout;
import com.tlab.viewtohardwarebuffer.ViewToHWBRenderer;
import com.unity3d.player.UnityPlayer;

import java.io.File;
import java.io.IOException;

public class ViewPlugin extends UnityPluginObject {
    private ViewToHWBRenderer mRenderer;
    private CustomGLSurfaceView mGLSurfaceView;
    //Layout
    private RelativeLayout mLayout;
    private GLLinearLayout mGlLinearLayout;
    //Tex var
    private int mTexWidth;
    private int mTexHeight;
    private int mScreenWidth;
    private int mScreenHeight;
    private SharedTexture mSharedTexture;
    private HardwareBuffer mShareBuffer;
    private int[] mHWBFboTextureId;
    private int[] mHWBFboID;
    //my vars
    private boolean mIsPaused = false;
    //Lifecycle
    public ViewPlugin(PluginManager p, Activity a, int textureWidth, int textureHeight, int screenWidth, int screenHeight) {
        super(p, a);
        mTexWidth = textureWidth;
        mTexHeight = textureHeight;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        LimeLog.severe("Initializing ViewManager");
        LimeLog.info("Texture width: " + mTexWidth + " Texture height: " + mTexHeight + " Screen width: " + mScreenWidth + " Screen height: " + mScreenHeight);
        onCreate();
        isInitialized = true;
    }

    @Override
    protected void onCreate() {

        UnityPlayer.currentActivity.runOnUiThread(() -> {
            mRenderer = new ViewToHWBRenderer();
            mRenderer.SetTextureResolution(mTexWidth, mTexHeight);

            mLayout = new RelativeLayout(UnityPlayer.currentActivity);
            mLayout.setGravity(Gravity.TOP);
            mLayout.setX(mScreenWidth);
            mLayout.setY(mScreenHeight);
            mLayout.setBackgroundColor(0xFFFFFFFF);

            mGLSurfaceView = new CustomGLSurfaceView(UnityPlayer.currentActivity);
            mGLSurfaceView.setEGLContextClientVersion(3);
            mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            mGLSurfaceView.setPreserveEGLContextOnPause(true);
            mGLSurfaceView.setRenderer(mRenderer);
            mGLSurfaceView.setBackgroundColor(0x00000000);

            mGlLinearLayout = new GLLinearLayout(UnityPlayer.currentActivity, 1, 1);
            mGlLinearLayout.setViewToGLRenderer(mRenderer);
            mGlLinearLayout.setGravity(Gravity.START);
            mGlLinearLayout.setOrientation(GLLinearLayout.HORIZONTAL);
            mGlLinearLayout.setBackgroundColor(Color.RED);

            //TODO: Initize view and aad it to gl layout here
//            initMyView(mGlLinearLayout);

            UnityPlayer.currentActivity.addContentView(mLayout, new RelativeLayout.LayoutParams(mTexWidth, mTexHeight));
            mLayout.addView(mGLSurfaceView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mLayout.addView(mGlLinearLayout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            LimeLog.info("Finished init3");

        });
    }

    @Override
    public void onDestroy() {
        UnityPlayer.currentActivity.runOnUiThread(() -> {
            if (mSharedTexture != null) {
                mSharedTexture.release();
                mSharedTexture = null;
            }
        });
    }

    @Override
    public void onResume() {
        mIsPaused = false;
    }

    @Override
    public void onPause() {
        mIsPaused = true;
    }

    ////////Texture methods
    public void updateSurface() {
        if (!isInitialized || mIsPaused)
            return;
        mGlLinearLayout.postInvalidate();
    }

    public int getTexturePtr() {
        updateSurface();
        return mHWBFboTextureId == null ? 0 : mHWBFboTextureId[0];
    }

    public void releaseSharedTexture() {
        if (mHWBFboTextureId != null) {
            GLES30.glDeleteTextures(mHWBFboTextureId.length, mHWBFboTextureId, 0);
            mHWBFboTextureId = null;
        }

        if (mHWBFboID != null) {
            GLES30.glDeleteTextures(mHWBFboID.length, mHWBFboID, 0);
            mHWBFboID = null;
        }

        if (mSharedTexture != null) {
            mSharedTexture.release();
            mSharedTexture = null;
        }
    }

    public void updateSharedTexture() {
        HardwareBuffer sb = mRenderer.getHardwareBuffer();
        if (sb == null || mShareBuffer == sb) {
            return;
        }
        releaseSharedTexture();

        mHWBFboTextureId = new int[1];
        mHWBFboID = new int[1];

        GLES30.glGenTextures(mHWBFboTextureId.length, mHWBFboTextureId, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mHWBFboTextureId[0]);

        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        SharedTexture sharedTexture = new SharedTexture(sb);

        sharedTexture.bindTexture(mHWBFboTextureId[0]);
        mSharedTexture = sharedTexture;
        mShareBuffer = sb;
    }

    //Misc Tex methods
    public void resizeTex(int w, int h) {
        mTexWidth = w;
        mTexHeight = h;

        UnityPlayer.currentActivity.runOnUiThread(() -> {
            if (mRenderer != null) {
                mRenderer.SetTextureResolution(mTexWidth, mTexHeight);
                mRenderer.requestResize();
            }
        });
    }
//End of Class
}
