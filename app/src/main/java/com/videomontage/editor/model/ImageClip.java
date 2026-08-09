package com.videomontage.editor.model;

import com.videomontage.core.Ids;

import java.util.List;

public final class ImageClip extends Clip {
    public final String sourcePath;

    public ImageClip(ClipTiming timing, Transform transform, List<Effect> effects,
                     String label, String sourcePath) {
        this(Ids.newId(), timing, transform, effects, label, sourcePath);
    }

    public ImageClip(String id, ClipTiming timing, Transform transform,
                      List<Effect> effects, String label, String sourcePath) {
        super(id, timing, transform, effects, 0f, label);
        this.sourcePath = sourcePath;
    }

    @Override
    public Clip withTiming(ClipTiming t) {
        return new ImageClip(id, t, transform, effects, label, sourcePath);
    }

    @Override
    public Clip withEffects(List<Effect> e) {
        return new ImageClip(id, timing, transform, e, label, sourcePath);
    }

    @Override
    public Clip withNewId() {
        return new ImageClip(timing, transform, effects, label, sourcePath);
    }
}
