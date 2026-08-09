package com.videomontage.editor.model;

import com.videomontage.core.Ids;

import java.util.Collections;
import java.util.List;

public final class TextClip extends Clip {
    public final String text;
    public final String fontFamily;
    public final float sizeSp;
    public final int colorArgb;

    public TextClip(ClipTiming timing, Transform transform, String label,
                    String text, String fontFamily, float sizeSp, int colorArgb) {
        this(Ids.newId(), timing, transform, label, text, fontFamily, sizeSp, colorArgb);
    }

    public TextClip(String id, ClipTiming timing, Transform transform, String label,
                     String text, String fontFamily, float sizeSp, int colorArgb) {
        super(id, timing, transform, Collections.<Effect>emptyList(), 0f, label);
        this.text = text;
        this.fontFamily = fontFamily;
        this.sizeSp = sizeSp;
        this.colorArgb = colorArgb;
    }

    @Override
    public Clip withTiming(ClipTiming t) {
        return new TextClip(id, t, transform, label, text, fontFamily, sizeSp, colorArgb);
    }

    @Override
    public Clip withEffects(List<Effect> e) { return this; }

    @Override
    public Clip withNewId() {
        return new TextClip(timing, transform, label, text, fontFamily, sizeSp, colorArgb);
    }
}
