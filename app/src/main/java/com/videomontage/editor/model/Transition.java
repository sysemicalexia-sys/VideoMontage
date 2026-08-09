package com.videomontage.editor.model;

/** Sits on the boundary between two adjacent clips; each contributes
 *  `durationMs / 2` of overlap. */
public final class Transition {

    public enum Kind {
        NONE, DISSOLVE, FADE_BLACK, FADE_WHITE,
        WIPE_LEFT, WIPE_RIGHT, SLIDE_PUSH, ZOOM_IN, ZOOM_OUT, BLUR_DISSOLVE
    }

    public final Kind kind;
    public final long durationMs;

    public Transition(Kind kind, long durationMs) {
        this.kind = kind;
        this.durationMs = durationMs;
    }

    public static Transition none() {
        return new Transition(Kind.NONE, 0);
    }
}
