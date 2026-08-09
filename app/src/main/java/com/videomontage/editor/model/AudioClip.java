package com.videomontage.editor.model;

import com.videomontage.core.Ids;

import java.util.Collections;
import java.util.List;

public final class AudioClip extends Clip {
    public final String sourcePath;
    /** Normalized peaks, ~100 buckets per second, for the lane drawing. */
    public final float[] waveformPeaks;
    public final long fadeInMs;
    public final long fadeOutMs;

    public AudioClip(ClipTiming timing, float volume, String label, String sourcePath,
                     float[] waveformPeaks, long fadeInMs, long fadeOutMs) {
        this(Ids.newId(), timing, volume, label, sourcePath, waveformPeaks, fadeInMs, fadeOutMs);
    }

    public AudioClip(String id, ClipTiming timing, float volume, String label,
                      String sourcePath, float[] waveformPeaks, long fadeInMs, long fadeOutMs) {
        super(id, timing, Transform.identity(), Collections.<Effect>emptyList(), volume, label);
        this.sourcePath = sourcePath;
        this.waveformPeaks = waveformPeaks;
        this.fadeInMs = fadeInMs;
        this.fadeOutMs = fadeOutMs;
    }

    @Override
    public boolean isVisual() { return false; }

    @Override
    public Clip withTiming(ClipTiming t) {
        return new AudioClip(id, t, volume, label, sourcePath, waveformPeaks, fadeInMs, fadeOutMs);
    }

    @Override
    public Clip withEffects(List<Effect> e) { return this; }

    @Override
    public Clip withNewId() {
        return new AudioClip(timing, volume, label, sourcePath, waveformPeaks, fadeInMs, fadeOutMs);
    }
}
