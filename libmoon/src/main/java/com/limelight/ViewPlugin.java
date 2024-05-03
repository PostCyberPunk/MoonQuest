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
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.SurfaceHolder;
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

        mActivity.runOnUiThread(() -> {
            mRenderer = new ViewToHWBRenderer();
            mRenderer.SetTextureResolution(mTexWidth, mTexHeight);

            mLayout = new RelativeLayout(mActivity);
            mLayout.setGravity(Gravity.TOP);
            mLayout.setX(mScreenWidth);
            mLayout.setY(mScreenHeight);
            mLayout.setBackgroundColor(0xFFFFFFFF);

            mGLSurfaceView = new CustomGLSurfaceView(mActivity);
            mGLSurfaceView.setEGLContextClientVersion(3);
            mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            mGLSurfaceView.setPreserveEGLContextOnPause(true);
            mGLSurfaceView.setRenderer(mRenderer);
            mGLSurfaceView.setBackgroundColor(0x00000000);

            mGlLinearLayout = new GLLinearLayout(mActivity, 1, 1);
            mGlLinearLayout.setViewToGLRenderer(mRenderer);
            mGlLinearLayout.setGravity(Gravity.START);
            mGlLinearLayout.setOrientation(GLLinearLayout.HORIZONTAL);
            mGlLinearLayout.setBackgroundColor(Color.RED);

            //TODO: Initize view and aad it to gl layout here
            initMyView(mGlLinearLayout);
//            initMovieView(mGlLinearLayout);
//            LoadImage("/sdcard/Pictures/1.jpg");
            mPluginManager.mLayout = mGlLinearLayout;

            mActivity.addContentView(mLayout, new RelativeLayout.LayoutParams(mTexWidth, mTexHeight));
            mLayout.addView(mGLSurfaceView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mLayout.addView(mGlLinearLayout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            LimeLog.info("Finished init3");

        });
    }

    @Override
    public void onDestroy() {
        mActivity.runOnUiThread(() -> {
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

        mActivity.runOnUiThread(() -> {
            if (mRenderer != null) {
                mRenderer.SetTextureResolution(mTexWidth, mTexHeight);
                mRenderer.requestResize();
            }
        });
    }

    //My View
    private ImageView mImageView;

    private void initMyView(GLLinearLayout l) {
        mImageView = new ImageView(UnityPlayer.currentActivity);
        l.addView(mImageView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
    }

    private void destroyMyView() {
        mImageView = null;
    }

    public void LoadImage(String path) {
        UnityPlayer.currentActivity.runOnUiThread(() -> {
            if (!new File(path).exists()) {
                LimeLog.severe("File not found");
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) {
                LimeLog.severe("Failed to load image");
                return;
            }
            LimeLog.info("Image loaded" + bitmap.getWidth() + "," + bitmap.getHeight() + "at" + path);
            bitmap = Bitmap.createScaledBitmap(bitmap, mTexWidth, mTexHeight, false);
            mImageView.setImageBitmap(bitmap);
            LimeLog.info("Image load at" + path);
        });
    }

    private MediaPlayer mMediaPlayer;
    private SurfaceView mSurfaceView;

    private void initMovieView(GLLinearLayout l) {
        mSurfaceView = new SurfaceView(UnityPlayer.currentActivity);
        l.addView(mSurfaceView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initPlayer();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
            // Other methods...
        });
    }

    private void initPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
        }
        if (mSurfaceView != null) {
            mMediaPlayer.setSurface(mSurfaceView.getHolder().getSurface());
        }
        try {
            File file = new File("/sdcard/Pictures/q.mp4");
//            mMediaPlayer.setAudioStreamType(.STREAM_MUSIC);
//            mMediaPlayer.setDataSource(Uri.fromFile(file).toString());
            mMediaPlayer.setDataSource("/sdcard/Pictures/q.mp4");
            mMediaPlayer.prepareAsync();
            LimeLog.severe("Playing video");
        } catch (IOException e) {
            LimeLog.severe("Opps:" + e);
        }
    }
//End of Class
}
