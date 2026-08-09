package com.videomontage.editor.model;

import com.videomontage.core.Ids;

import java.util.List;

public final class VideoClip extends Clip {
    public final String sourcePath;
    public final long sourceDurationMs;
    public final boolean hasEmbeddedAudio;

    public VideoClip(ClipTiming timing, Transform transform, List<Effect> effects,
                     float volume, String label, String sourcePath,
                     long sourceDurationMs, boolean hasEmbeddedAudio) {
        this(Ids.newId(), timing, transform, effects, volume, label,
                sourcePath, sourceDurationMs, hasEmbeddedAudio);
    }

    public VideoClip(String id, ClipTiming timing, Transform transform,
                      List<Effect> effects, float volume, String label,
                      String sourcePath, long sourceDurationMs, boolean hasEmbeddedAudio) {
        super(id, timing, transform, effects, volume, label);
        this.sourcePath = sourcePath;
        this.sourceDurationMs = sourceDurationMs;
        this.hasEmbeddedAudio = hasEmbeddedAudio;
    }

    @Override
    public Clip withTiming(ClipTiming t) {
        return new VideoClip(id, t, transform, effects, volume, label,
                sourcePath, sourceDurationMs, hasEmbeddedAudio);
    }

    @Override
    public Clip withEffects(List<Effect> e) {
        return new VideoClip(id, timing, transform, e, volume, label,
                sourcePath, sourceDurationMs, hasEmbeddedAudio);
    }

    @Override
    public Clip withNewId() {
        return new VideoClip(timing, transform, effects, volume, label,
                sourcePath, sourceDurationMs, hasEmbeddedAudio);
    }
}
