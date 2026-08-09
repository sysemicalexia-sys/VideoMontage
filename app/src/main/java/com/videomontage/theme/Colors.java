package com.videomontage.theme;

import android.graphics.Color;

public final class Colors {
    private Colors() {}

    public static final int BG_APP = Color.rgb(0x09, 0x09, 0x0B);
    public static final int INK = BG_APP;
    public static final int GRAPHITE = Color.rgb(0x18, 0x18, 0x1B);
    public static final int CHARCOAL = Color.rgb(0x27, 0x27, 0x2A);
    public static final int SLATE = Color.rgb(0x3F, 0x3F, 0x46);
    public static final int HAIRLINE = 0x14FFFFFF;

    public static final int ACCENT = Color.rgb(0x3B, 0x82, 0xF6);
    public static final int ACCENT_VIOLET = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int ACCENT_DIM = Color.rgb(0x1D, 0x4E, 0xD8);
    public static final int VIOLET = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int TEAL = Color.rgb(0x2D, 0xD4, 0xBF);
    public static final int TEXT_PRIMARY = Color.rgb(0xFA, 0xFA, 0xFA);
    public static final int TEXT_SECONDARY = Color.rgb(0xA1, 0xA1, 0xAA);
    public static final int TEXT_TERTIARY = Color.rgb(0x71, 0x71, 0x7A);

    public static final int TRACK_VIDEO = Color.rgb(0x25, 0x63, 0xEB);
    public static final int TRACK_AUDIO = Color.rgb(0x10, 0xB9, 0x81);
    public static final int TRACK_TEXT = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int TRACK_FX = Color.rgb(0xF5, 0x9E, 0x0B);

    public static int forTrack(com.videomontage.editor.model.Track.Kind kind) {
        switch (kind) {
            case AUDIO: return TRACK_AUDIO;
            case TEXT:  return TRACK_TEXT;
            case FX:    return TRACK_FX;
            default:    return TRACK_VIDEO;
        }
    }
}
