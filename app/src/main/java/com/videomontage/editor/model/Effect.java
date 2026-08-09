package com.videomontage.editor.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A shader node plus parameters. Static values live in `params`; anything
 *  animated lives in `keyframes`. The native side sees only the flattened
 *  [kind, p0..p3] run per frame. */
public final class Effect {

    /** Numeric ids match the native renderer's dispatch (1=grade, 2=blur). */
    public enum Kind {
        EXPOSURE(1), CONTRAST(1), SATURATION(1), TEMPERATURE(1),
        BLUR(2), VIGNETTE(3), GRAIN(4), SHARPEN(5),
        LUT(6), CHROMA_KEY(7), GLITCH(8);

        public final int shaderId;
        Kind(int shaderId) { this.shaderId = shaderId; }
    }

    public final Kind kind;
    public final Map<String, Float> params;
    public final List<KeyframeTrack> keyframes;
    public final long rangeStartMs;
    public final long rangeEndMs;

    public Effect(Kind kind, Map<String, Float> params, List<KeyframeTrack> keyframes,
                  long rangeStartMs, long rangeEndMs) {
        this.kind = kind;
        this.params = Collections.unmodifiableMap(new HashMap<>(params));
        this.keyframes = Collections.unmodifiableList(keyframes);
        this.rangeStartMs = rangeStartMs;
        this.rangeEndMs = rangeEndMs;
    }

    public static Effect simple(Kind kind, float value) {
        Map<String, Float> p = new HashMap<>();
        p.put("amount", value);
        return new Effect(kind, p, Collections.<KeyframeTrack>emptyList(), 0, Long.MAX_VALUE);
    }

    public boolean appliesAt(long tMs) {
        return tMs >= rangeStartMs && tMs <= rangeEndMs;
    }
}
