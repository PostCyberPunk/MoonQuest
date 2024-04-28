package com.pcp.libmoonlight;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    /**
     * @param context
     */
    public MySurfaceView(Context context) {
        super(context);
        initView();
    }

    /**
     * @param context
     * @param attrs
     */
    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        initView();
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public MySurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        initView();
    }

    /**
     * @return
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return false;
    }

    /**
     * @param canvas the canvas on which the background will be drawn
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    private void initView() {
        SurfaceHolder mHodler = getHolder();
        mHodler.addCallback(this);
    }
}
