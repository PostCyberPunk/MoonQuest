package com.pcp.libmoonlight;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.VideoView;

public class BitmapVideoView extends VideoView {

    /**
     * @param context
     */
    public BitmapVideoView(Context context) {
        super(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public BitmapVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public BitmapVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * @param canvas the canvas on which the background will be drawn
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
