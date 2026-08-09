package com.videomontage.editor.model;

/** Normalized rect in source space, 0..1. */
public final class CropRect {
    public final float left, top, right, bottom;

    public CropRect(float left, float top, float right, float bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }

    public static CropRect full() {
        return new CropRect(0f, 0f, 1f, 1f);
    }
}
