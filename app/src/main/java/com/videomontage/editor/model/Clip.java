package com.videomontage.editor.model;

import java.util.Collections;
import java.util.List;

/** Base of the clip hierarchy. Clips are immutable — every edit returns a
 *  new instance, and undo is snapshot-based over the owning timeline. */
public abstract class Clip {
    public final String id;
    public final ClipTiming timing;
    public final Transform transform;
    public final List<Effect> effects;
    public final float volume;
    public final String label;

    protected Clip(String id, ClipTiming timing, Transform transform,
                   List<Effect> effects, float volume, String label) {
        this.id = id;
        this.timing = timing;
        this.transform = transform;
        this.effects = Collections.unmodifiableList(effects);
        this.volume = volume;
        this.label = label;
    }

    public abstract Clip withTiming(ClipTiming t);
    public abstract Clip withEffects(List<Effect> e);
    public abstract Clip withNewId();

    /** True for clips that produce video layers. */
    public boolean isVisual() { return true; }
}
