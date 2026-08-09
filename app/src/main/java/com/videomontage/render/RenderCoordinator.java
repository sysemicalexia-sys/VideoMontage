package com.videomontage.render;

import android.view.Surface;

import com.videomontage.editor.model.Timeline;
import com.videomontage.nativecore.LayerMarshaller;
import com.videomontage.nativecore.NativeEngine;
import com.videomontage.timeline.PlaybackClock;

/** Owns the per-frame render decision: which instant, which layers. The
 *  preview view calls renderFrame() from its GL thread; everything GL is
 *  native, so this class stays thread-agnostic. */
public final class RenderCoordinator {

    private final LayerMarshaller marshaller = new LayerMarshaller();
    private Timeline timeline = Timeline.empty();
    private PlaybackClock clock;
    private int canvasWidth = 1920, canvasHeight = 1080;
    private boolean initialized;

    public void setClock(PlaybackClock clock) {
        this.clock = clock;
        this.clock.setDuration(timeline.durationMs());
    }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
        marshaller.prune(timeline);
        if (clock != null) clock.setDuration(timeline.durationMs());
        if (initialized) NativeEngine.nativeInvalidate();
    }

    public void attachSurface(Surface surface, int width, int height) {
        canvasWidth = width;
        canvasHeight = height;
        if (!NativeEngine.ensureLoaded()) return; // dark preview; editor survives
        if (!initialized) initialized = NativeEngine.nativeInit(width, height);
        NativeEngine.nativeAttachPreview(surface);
    }

    public void detachSurface() {
        if (initialized) NativeEngine.nativeDetachPreview();
    }

    /** Called on the GL thread once per vsync while visible. */
    public void renderFrame() {
        if (!initialized || clock == null) return;
        long t = clock.positionMs();
        if (clock.isPlaying()) clock.onFrameRendered();
        if (marshaller.marshal(timeline, t)) {
            marshaller.renderAt(t);
        } else {
            marshaller.renderAt(t); // zero layers → native clears to canvas
        }
    }

    /** Frame-accurate seek render while paused. */
    public void renderSeek(long tMs) {
        if (!initialized) return;
        if (marshaller.marshal(timeline, tMs)) marshaller.renderAt(tMs);
    }

    public void shutdown() {
        if (initialized) {
            NativeEngine.nativeShutdown();
            initialized = false;
        }
    }
}
