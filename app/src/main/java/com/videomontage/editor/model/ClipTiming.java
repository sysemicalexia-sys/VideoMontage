package com.videomontage.editor.model;

/** Timing of a clip on the timeline, all times in milliseconds.
 *  `trimInMs` is where playback begins inside the source; content runs
 *  at `speed`. */
public final class ClipTiming {
    public final long positionMs;
    public final long durationMs;
    public final long trimInMs;
    public final float speed;

    public ClipTiming(long positionMs, long durationMs, long trimInMs, float speed) {
        if (durationMs <= 0) throw new IllegalArgumentException("duration must be positive");
        if (speed <= 0f) throw new IllegalArgumentException("speed must be positive");
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.trimInMs = trimInMs;
        this.speed = speed;
    }

    public long endMs() { return positionMs + durationMs; }

    /** Maps a timeline instant to source time. */
    public long sourceTimeAt(long timelineMs) {
        return trimInMs + (long) ((timelineMs - positionMs) * speed);
    }

    public ClipTiming moved(long newPositionMs) {
        return new ClipTiming(newPositionMs, durationMs, trimInMs, speed);
    }

    public ClipTiming withDuration(long newDurationMs) {
        return new ClipTiming(positionMs, newDurationMs, trimInMs, speed);
    }

    public ClipTiming withStart(long newPositionMs, long newDurationMs, long newTrimInMs) {
        return new ClipTiming(newPositionMs, newDurationMs, newTrimInMs, speed);
    }

    public ClipTiming withSpeed(float newSpeed) {
        return new ClipTiming(positionMs, durationMs, trimInMs, newSpeed);
    }
}
