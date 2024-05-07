package com.liblime.stream;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLExt;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.Surface;

import com.limelight.LimeLog;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

// An attempt to share EGLContext and update textures from a Frame Buffer Object.

public class StreamView extends GLSurfaceView {

    private static final String TAG = "libmoonlight";
    private StreamRenderer mRenderer;

    public StreamView(Context context) {
        super(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    private int[] getContextAttributes() {
        // https://developer.android.com/reference/android/opengl/EGL14#EGL_CONTEXT_CLIENT_VERSION
        return new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION /* 0x3098 */, 3,
                EGL15.EGL_CONTEXT_MAJOR_VERSION, 3,
                EGL15.EGL_CONTEXT_MINOR_VERSION, 2,
                EGLExt.EGL_CONTEXT_MAJOR_VERSION_KHR, 3,
                EGLExt.EGL_CONTEXT_MINOR_VERSION_KHR, 2,
                EGL10.EGL_NONE};
    }

    private final EGLContextFactory mEGLContextFactory = new EGLContextFactory() {
        /**
         *
         * @param egl
         * @param display
         * @param context
         */
        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
        }

        /**
         *
         * @param egl
         * @param display
         * @param config
         * @return
         */
        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            // Here, I wanted to set Unity's EGLContext as a shared context so that Texture2D could be updated with a pointer.
            // This worked well on some devices and not on others.
            // EGL_BAD_MATCH (Oculus quest 2)

            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, getContextAttributes());

            return context;
        }
    };

    @Override
    public void setRenderer(Renderer renderer) {
        setEGLContextFactory(mEGLContextFactory);
        super.setRenderer(renderer);
        mRenderer = (StreamRenderer) renderer;
    }

    public Surface getSurface() {
        return mRenderer.getSurface();
    }
    public void requstRender() {
        super.requestRender();
    }

    double desiredAspectRatio;
    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int measuredHeight, measuredWidth;
        LimeLog.temp("onMeasure: widthSize: " + widthSize + ", heightSize: " + heightSize);
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
