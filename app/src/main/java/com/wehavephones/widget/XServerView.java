package com.wehavephones.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.wehavephones.renderer.GLRenderer;
import com.wehavephones.xserver.XServer;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private final GLRenderer renderer;

    public XServerView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Asks the panel for its top refresh rate (120 Hz on the S26 Ultra). Without
     * this, adaptive refresh may drop the game surface to 60 Hz and every frame
     * the game renders above that is thrown away.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Display display = getDisplay();
        if (display == null) return;
        float best = 0;
        for (Display.Mode mode : display.getSupportedModes()) best = Math.max(best, mode.getRefreshRate());
        if (best <= 0) return;
        try {
            holder.getSurface().setFrameRate(best, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }
        catch (RuntimeException ignored) {}
    }

    public GLRenderer getRenderer() {
        return renderer;
    }
}
