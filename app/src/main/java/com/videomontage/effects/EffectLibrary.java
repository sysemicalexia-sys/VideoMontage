package com.videomontage.effects;

import com.videomontage.editor.model.Effect;

import java.util.ArrayList;
import java.util.List;

/** Curated presets. Each preset is just a tuned Effect — the pipeline
 *  treats presets and hand-built effects identically. */
public final class EffectLibrary {

    public static final class Preset {
        public final String name;
        public final Effect effect;

        Preset(String name, Effect effect) {
            this.name = name;
            this.effect = effect;
        }
    }

    private EffectLibrary() {}

    public static List<Preset> colorPresets() {
        List<Preset> out = new ArrayList<>();
        out.add(new Preset("Cinema", grade(0.15f, 1.18f, 0.92f, -0.12f)));
        out.add(new Preset("Teal & Orange", grade(0.05f, 1.1f, 1.15f, 0.2f)));
        out.add(new Preset("Noir", grade(-0.1f, 1.35f, 0.0f, 0f)));
        out.add(new Preset("Golden Hour", grade(0.2f, 1.05f, 1.1f, 0.45f)));
        out.add(new Preset("Frost", grade(0.1f, 0.95f, 0.85f, -0.5f)));
        out.add(new Preset("Vivid", grade(0f, 1.12f, 1.5f, 0f)));
        return out;
    }

    public static List<Preset> stylePresets() {
        List<Preset> out = new ArrayList<>();
        out.add(new Preset("Soft Focus", Effect.simple(Effect.Kind.BLUR, 2.5f)));
        out.add(new Preset("Dream", Effect.simple(Effect.Kind.BLUR, 1.2f)));
        out.add(new Preset("Vignette", Effect.simple(Effect.Kind.VIGNETTE, 0.45f)));
        out.add(new Preset("Film Grain", Effect.simple(Effect.Kind.GRAIN, 0.3f)));
        out.add(new Preset("Sharpen", Effect.simple(Effect.Kind.SHARPEN, 0.6f)));
        return out;
    }

    private static Effect grade(float exposure, float contrast, float saturation, float temp) {
        java.util.Map<String, Float> p = new java.util.HashMap<>();
        p.put("exposure", exposure);
        p.put("contrast", contrast);
        p.put("saturation", saturation);
        p.put("temperature", temp);
        return new Effect(Effect.Kind.EXPOSURE, p,
                java.util.Collections.<com.videomontage.editor.model.KeyframeTrack>emptyList(),
                0, Long.MAX_VALUE);
    }
}
