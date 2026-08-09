package com.videomontage.widgets;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.videomontage.render.RenderCoordinator;
import com.videomontage.utils.Spring;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class PreviewView extends GLSurfaceView {

    public interface GestureListener {
        void onSingleTap();
        void onZoomChanged(float zoom, float focusX, float focusY);
    }

    private RenderCoordinator coordinator;
    private GestureListener gestureListener;
    private final Spring zoom = Spring.standard(1f);

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector tapDetector;

    public PreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        // setRenderMode() happens in setCoordinator(), AFTER setRenderer().
        // Calling it here NPEs: GLThread doesn't exist until setRenderer().

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom.setTarget(Math.max(1f, Math.min(5f,
                        zoom.value() * detector.getScaleFactor())));
                notifyZoom(detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        tapDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (gestureListener != null) gestureListener.onSingleTap();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                zoom.setTarget(1f);
                notifyZoom(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setCoordinator(RenderCoordinator coordinator) {
        this.coordinator = coordinator;
        setRenderer(new Renderer() {
            @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {}
            @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
                if (PreviewView.this.coordinator != null) {
                    PreviewView.this.coordinator.attachSurface(
                            getHolder().getSurface(), width, height);
                }
            }
            @Override public void onDrawFrame(GL10 gl) {
                if (PreviewView.this.coordinator != null) {
                    PreviewView.this.coordinator.renderFrame();
                }
            }
        });
        setRenderMode(RENDERMODE_CONTINUOUSLY); // renderer exists now: safe
    }

    public void setGestureListener(GestureListener l) {
        gestureListener = l;
    }

    private void notifyZoom(float fx, float fy) {
        if (gestureListener != null) {
            gestureListener.onZoomChanged(zoom.target(),
                    fx / getWidth(), fy / getHeight());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        tapDetector.onTouchEvent(event);
        return true;
    }

    public void detach() {
        if (coordinator != null) queueEvent(new Runnable() {
            @Override public void run() { coordinator.detachSurface(); }
        });
    }
}
