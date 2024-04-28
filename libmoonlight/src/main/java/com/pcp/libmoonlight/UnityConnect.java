package com.pcp.libmoonlight;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.opengl.GLES30;
import android.os.Build;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.robot9.shared.SharedTexture;
import com.tlab.viewtohardwarebuffer.CustomGLSurfaceView;
import com.tlab.viewtohardwarebuffer.GLLinearLayout;
import com.tlab.viewtohardwarebuffer.ViewToHWBRenderer;
import com.unity3d.player.UnityPlayer;

import java.io.File;
import java.io.IOException;


public class UnityConnect extends Fragment implements TextureView.SurfaceTextureListener, MoviePlayer.PlayerFeedback {

    // ---------------------------------------------------------------------------------------------------------
    // Renderer
    //

    private ViewToHWBRenderer mViewToHWBRenderer;
    private CustomGLSurfaceView mGLSurfaceView;

    // ---------------------------------------------------------------------------------------------------------
    // Views.
    //


    // ---------------------------------------------------------------------------------------------------------
    // View Group
    //

    private RelativeLayout mLayout;
    private GLLinearLayout mGlLayout;

    // ---------------------------------------------------------------------------------------------------------
    // Tex variables.
    //

    private int mTexWidth;
    private int mTexHeight;
    private int mScreenWidth;
    private int mScreenHeight;

    private boolean mInitialized = false;

    private SharedTexture mSharedTexture;
    private HardwareBuffer mSharedBuffer;

    private int[] mHWBFboTexID;
    private int[] mHWBFboID;

    private String mFilePath;
    private final static String TAG = "libmoonlight";

    // ---------------------------------------------------------------------------------------------------------
    // Initialize this class
    //

    /**
     *
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void initialize(int textureWidth, int textureHeight, int screenWidth, int screenHeight, String path) {
        mTexWidth = textureWidth;
        mTexHeight = textureHeight;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mFilePath = path;
        LimeLog.info("Texture width: " + mTexWidth + " Texture height: " + mTexHeight + " Screen width: " + mScreenWidth + " Screen height: " + mScreenHeight);
        LimeLog.info("File Path:" + mFilePath);
        initView();
    }

    /**
     * @return
     */
    public boolean IsInitialized() {
        return mInitialized;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Shared Texture Utility
    //

    public void releaseSharedTexture() {
        if (mHWBFboTexID != null) {
            GLES30.glDeleteTextures(mHWBFboTexID.length, mHWBFboTexID, 0);
            mHWBFboTexID = null;
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

    /**
     *
     */
    public void updateSharedTexture() {

        HardwareBuffer sharedBuffer = mViewToHWBRenderer.getHardwareBuffer();

        if (sharedBuffer == null || mSharedBuffer == sharedBuffer) {
            return;
        }

        releaseSharedTexture();

        mHWBFboTexID = new int[1];
        mHWBFboID = new int[1];
        GLES30.glGenTextures(mHWBFboTexID.length, mHWBFboTexID, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mHWBFboTexID[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        SharedTexture sharedTexture = new SharedTexture(sharedBuffer);

        sharedTexture.bindTexture(mHWBFboTexID[0]);

        mSharedTexture = sharedTexture;
        mSharedBuffer = sharedBuffer;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Initialize streamView
    //
    private TextureView streamView;

    /**
     *
     */
    private void initView() {
        final UnityConnect self = this;

        // -----------------------------------------------------------
        // Hierarchical structure.
        // parent -----
        //            |
        //            |
        //            | mLayout -----
        //                          |
        //                          |
        //                          | mGlLayout -----
        //                          |               |
        //                          |               |
        //                          |               | mView
        //                          |
        //                          |
        //                          | mGLSurfaceView

        UnityPlayer.currentActivity.runOnUiThread(() -> {

            mViewToHWBRenderer = new ViewToHWBRenderer();
            mViewToHWBRenderer.SetTextureResolution(mTexWidth, mTexHeight);

            mLayout = new RelativeLayout(UnityPlayer.currentActivity);
            mLayout.setGravity(Gravity.TOP);
            mLayout.setX(mScreenWidth);
            mLayout.setY(mScreenHeight);
            mLayout.setBackgroundColor(0xFFFFFFFF);

            mGLSurfaceView = new CustomGLSurfaceView(UnityPlayer.currentActivity);
            mGLSurfaceView.setEGLContextClientVersion(3);
            mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            mGLSurfaceView.setPreserveEGLContextOnPause(true);
            mGLSurfaceView.setRenderer(mViewToHWBRenderer);
            mGLSurfaceView.setBackgroundColor(0x00000000);

            mGlLayout = new GLLinearLayout(UnityPlayer.currentActivity, 1, 1);
            mGlLayout.setOrientation(GLLinearLayout.VERTICAL);
            mGlLayout.setGravity(Gravity.START);
            mGlLayout.setViewToGLRenderer(mViewToHWBRenderer);
            mGlLayout.setBackgroundColor(Color.GREEN);

            // if (mWebView == null) {
            //     mWebView = new BitmapWebView(UnityPlayer.currentActivity);
            // }

            // --------------------------------------------------------------------------------------------------------
            // Settings each View and View Group
            //

            // mGlLayout.addView(
            //         mWebView,
            //         new GLLinearLayout.LayoutParams(
            //                 GLLinearLayout.LayoutParams.MATCH_PARENT,
            //                 GLLinearLayout.LayoutParams.MATCH_PARENT
            //         )
            // );

            //check the file exists
            if (!new File(mFilePath).exists()) {
                LimeLog.severe("File does not exist");
            }

            streamView = new TextureView(UnityPlayer.currentActivity);
            streamView.setSurfaceTextureListener(this);

            UnityPlayer.currentActivity.addContentView(mLayout, new RelativeLayout.LayoutParams(mTexWidth, mTexHeight));
            mGlLayout.addView(streamView, new GLLinearLayout.LayoutParams(GLLinearLayout.LayoutParams.MATCH_PARENT, GLLinearLayout.LayoutParams.MATCH_PARENT));
            mLayout.addView(mGLSurfaceView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mLayout.addView(mGlLayout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            LimeLog.info("Finished init2");
            mInitialized = true;
        });
    }

    /**
     *
     */
    public void Destroy() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            // if (mWebView == null) {
            //     return;
            // }

            // mWebView.stopLoading();
            // mGlLayout.removeView(mWebView);
            // mWebView.destroy();
            // mWebView = null;

            if (mSharedTexture != null) {
                mSharedTexture.release();
                mSharedTexture = null;
            }


        });
    }

    // ---------------------------------------------------------------------------------------------------------
    // java's unity interface.
    //

    /**
     *
     */
    public void updateSurface() {
        mGlLayout.postInvalidate();
    }

    /**
     * @return
     */
    public int getTexturePtr() {
        updateSurface();

        if (mHWBFboTexID == null) {
            return 0;
        }

        return mHWBFboTexID[0];
    }

    /**
     * @param textureWidth
     * @param textureHeight
     */
    public void resizeTex(int textureWidth, int textureHeight) {
        mTexWidth = textureWidth;
        mTexHeight = textureHeight;

        UnityPlayer.currentActivity.runOnUiThread(() -> {
            if (mViewToHWBRenderer == null) {
                return;
            }
            mViewToHWBRenderer.SetTextureResolution(mTexWidth, mTexHeight);
            mViewToHWBRenderer.requestResize();
        });
    }

    // public void resizeWeb(int webWidth, int webHeight) {
    //     mWebWidth = webWidth;
    //     mWebHeight = webHeight;
    //
    //     UnityPlayer.currentActivity.runOnUiThread(() -> {
    //         mGlLayout.mRatioWidth = (float) mTexWidth / mWebWidth;
    //         mGlLayout.mRatioHeight = (float) mTexHeight / mWebHeight;
    //
    //         ViewGroup.LayoutParams lp = mLayout.getLayoutParams();
    //         lp.width = mWebWidth;
    //         lp.height = mWebHeight;
    //
    //         mLayout.setLayoutParams(lp);
    //     });
    // }

    // ---------------------------------------------------------------------------------------------------------
    //
    //

    /**
     * @param left
     * @param top
     * @param right
     * @param bottom
     */
    private void SetMargins(int left, int top, int right, int bottom) {
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.NO_GRAVITY);
        params.setMargins(left, top, right, bottom);
        UnityPlayer.currentActivity.runOnUiThread(() -> {

            // if (mWebView == null) {
            //     return;
            // }
            // mWebView.setLayoutParams(params);
        });
    }

    private boolean mSurfaceTextureReady = false;

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        mSurfaceTextureReady = false;
        // assume activity is pausing, so don't need to update controls
        return true;    // caller should release ST
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // ignore
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        LimeLog.info("SurfaceTexture ready (" + width + "x" + height + ")");
        mSurfaceTextureReady = true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    private void adjustAspectRatio(int videoWidth, int videoHeight) {
        int viewWidth = streamView.getWidth();
        int viewHeight = streamView.getHeight();
        double aspectRatio = (double) videoHeight / videoWidth;

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;
        LimeLog.info(
                "video=" + videoWidth + "x" + videoHeight +
                        " view=" + viewWidth + "x" + viewHeight +
                        " newView=" + newWidth + "x" + newHeight +
                        " off=" + xoff + "," + yoff);

        Matrix txform = new Matrix();
        streamView.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        //txform.postRotate(10);          // just for fun
        // Haha
        txform.postTranslate(xoff, yoff);
        streamView.setTransform(txform);
        //FIXME:Layout should also set here
    }

    private MoviePlayer.PlayTask mPlayTask;

    public void playVideo() {
        UnityPlayer.currentActivity.runOnUiThread(() -> {
            LimeLog.info("starting movie");
            SurfaceTexture st = streamView.getSurfaceTexture();
            Surface surface = new Surface(st);
            MoviePlayer player = null;
            SpeedControlCallback callback = new SpeedControlCallback();
            callback.setFixedPlaybackRate(60);
            try {
                player = new MoviePlayer(
                        new File(mFilePath), surface, callback);
            } catch (IOException ioe) {
                LimeLog.severe("Unable to play movie::" + ioe);
                surface.release();
                return;
            }
            adjustAspectRatio(player.getVideoWidth(), player.getVideoHeight());

            mPlayTask = new MoviePlayer.PlayTask(player, this);
            mPlayTask.setLoopMode(true);
            mPlayTask.execute();
        });
    }


    @Override
    public void playbackStopped() {
        mPlayTask.setLoopMode(true);
    }
}
