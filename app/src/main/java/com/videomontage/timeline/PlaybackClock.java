package com.videomontage.timeline;

import android.os.SystemClock;

/** Monotonic position source. While playing, the render loop calls
 *  `onFrameRendered` — the renderer is the master clock so audio/video
 *  never drift. While paused, only explicit seeks move the position. */
public final class PlaybackClock {

    public interface Listener {
        void onPositionChanged(long positionMs);
        void onPlaybackEnded();
    }

    private long positionMs;
    private long durationMs;
    private boolean playing;
    private long playStartedAtUptimeMs;
    private long playStartedFromMs;
    private Listener listener;

    public void setDuration(long durationMs) {
        this.durationMs = durationMs;
        if (positionMs > durationMs) positionMs = durationMs;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public long positionMs() { return positionMs; }
    public boolean isPlaying() { return playing; }

    public void seek(long toMs) {
        positionMs = Math.max(0, Math.min(toMs, durationMs));
        if (playing) {
            playStartedAtUptimeMs = SystemClock.uptimeMillis();
            playStartedFromMs = positionMs;
        }
        if (listener != null) listener.onPositionChanged(positionMs);
    }

    public void play() {
        if (playing || durationMs <= 0) return;
        if (positionMs >= durationMs) positionMs = 0;
        playing = true;
        playStartedAtUptimeMs = SystemClock.uptimeMillis();
        playStartedFromMs = positionMs;
    }

    public void pause() {
        if (!playing) return;
        positionMs = currentPlayPosition();
        playing = false;
        if (listener != null) listener.onPositionChanged(positionMs);
    }

    /** Vsync tick from the render loop. */
    public void onFrameRendered() {
        if (!playing) return;
        positionMs = currentPlayPosition();
        if (positionMs >= durationMs) {
            positionMs = durationMs;
            playing = false;
            if (listener != null) {
                listener.onPositionChanged(positionMs);
                listener.onPlaybackEnded();
            }
        } else if (listener != null) {
            listener.onPositionChanged(positionMs);
        }
    }

    private long currentPlayPosition() {
        return playStartedFromMs + (SystemClock.uptimeMillis() - playStartedAtUptimeMs);
    }
}
