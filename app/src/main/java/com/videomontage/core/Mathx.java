package com.videomontage.core;

public final class Mathx {
    private Mathx() {}

    public static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /** Smoothstep — the only easing the scrub path needs; interactive
     *  motion is spring-driven on the view side. */
    public static float easeInOut(float t) {
        float x = clamp01(t);
        return x * x * (3f - 2f * x);
    }

    public static long clampLong(long v, long lo, long hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
