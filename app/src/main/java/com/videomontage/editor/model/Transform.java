package com.videomontage.editor.model;

/** Everything the compositor needs to place one clip on the canvas.
 *  Center is canvas-normalized (0.5, 0.5 = middle). Immutable — edits
 *  produce new instances. */
public final class Transform {
    public final float centerX, centerY, scaleX, scaleY, rotationDeg, opacity;
    public final CropRect crop;

    public Transform(float centerX, float centerY, float scaleX, float scaleY,
                     float rotationDeg, CropRect crop, float opacity) {
        this.centerX = centerX; this.centerY = centerY;
        this.scaleX = scaleX; this.scaleY = scaleY;
        this.rotationDeg = rotationDeg;
        this.crop = crop;
        this.opacity = opacity;
    }

    public static Transform identity() {
        return new Transform(0.5f, 0.5f, 1f, 1f, 0f, CropRect.full(), 1f);
    }

    public Transform withOpacity(float o) {
        return new Transform(centerX, centerY, scaleX, scaleY, rotationDeg, crop, o);
    }

    /** Column-major 4x4 for the renderer's uMvp. */
    public float[] toMvp() {
        float rad = (float) Math.toRadians(rotationDeg);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        float tx = centerX * 2f - 1f, ty = 1f - centerY * 2f;
        return new float[] {
            c * scaleX, s * scaleX, 0, 0,
            -s * scaleY, c * scaleY, 0, 0,
            0, 0, 1, 0,
            tx, ty, 0, 1,
        };
    }
}
