package com.videomontage.editor.model;

import com.videomontage.core.Mathx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A single animatable scalar on a clip (opacity, scale, rotation…).
 *  Vectors are decomposed into one track per component. */
public final class KeyframeTrack {

    public enum Property { OPACITY, SCALE_X, SCALE_Y, ROTATION, VOLUME, POSITION_X, POSITION_Y }

    public static final class Key {
        public final long atMs;
        public final float value;
        public final boolean smooth;

        public Key(long atMs, float value, boolean smooth) {
            this.atMs = atMs; this.value = value; this.smooth = smooth;
        }
    }

    public final Property property;
    public final List<Key> keys;

    public KeyframeTrack(Property property, List<Key> keys) {
        this.property = property;
        List<Key> sorted = new ArrayList<>(keys);
        Collections.sort(sorted, new Comparator<Key>() {
            @Override public int compare(Key a, Key b) {
                return Long.compare(a.atMs, b.atMs);
            }
        });
        this.keys = Collections.unmodifiableList(sorted);
    }

    public float valueAt(long tMs) {
        if (keys.isEmpty()) return defaultFor(property);
        Key first = keys.get(0);
        if (keys.size() == 1 || tMs <= first.atMs) return first.value;
        Key last = keys.get(keys.size() - 1);
        if (tMs >= last.atMs) return last.value;
        int i = 0;
        while (i < keys.size() && keys.get(i).atMs <= tMs) i++;
        Key a = keys.get(i - 1), b = keys.get(i);
        float raw = (float) (tMs - a.atMs) / (float) (b.atMs - a.atMs);
        return Mathx.lerp(a.value, b.value, a.smooth ? Mathx.easeInOut(raw) : raw);
    }

    private static float defaultFor(Property p) {
        switch (p) {
            case OPACITY: case SCALE_X: case SCALE_Y: case VOLUME: return 1f;
            default: return 0f;
        }
    }
}
