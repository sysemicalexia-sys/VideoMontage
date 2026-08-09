package com.videomontage.theme;

import android.graphics.Color;

/** Java mirror of res/values/colors.xml for custom views that draw with
 *  Paint — keep the two in sync, XML is the source of truth. */
public final class Colors {
    private Colors() {}

    public static final int INK        = Color.rgb(0x0A, 0x0B, 0x0E);
    public static final int GRAPHITE   = Color.rgb(0x11, 0x13, 0x18);
    public static final int CHARCOAL   = Color.rgb(0x17, 0x1A, 0x21);
    public static final int SLATE      = Color.rgb(0x1F, 0x23, 0x2D);
    public static final int HAIRLINE   = 0x14FFFFFF;

    public static final int ACCENT     = Color.rgb(0x4C, 0x8D, 0xFF);
    public static final int ACCENT_DIM = Color.rgb(0x2E, 0x5F, 0xD8);
    public static final int VIOLET     = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int TEAL       = Color.rgb(0x2D, 0xD4, 0xBF);

    public static final int TEXT_PRIMARY   = Color.rgb(0xF4, 0xF5, 0xF7);
    public static final int TEXT_SECONDARY = Color.rgb(0x9B, 0xA1, 0xAD);
    public static final int TEXT_TERTIARY  = Color.rgb(0x5C, 0x62, 0x70);

    public static final int TRACK_VIDEO = Color.rgb(0x31, 0x51, 0x8F);
    public static final int TRACK_AUDIO = Color.rgb(0x1E, 0x6E, 0x5F);
    public static final int TRACK_TEXT  = Color.rgb(0x5B, 0x3E, 0x8C);
    public static final int TRACK_FX    = Color.rgb(0x7A, 0x4A, 0x21);

    public static int forTrack(com.videomontage.editor.model.Track.Kind kind) {
        switch (kind) {
            case AUDIO: return TRACK_AUDIO;
            case TEXT:  return TRACK_TEXT;
            case FX:    return TRACK_FX;
            default:    return TRACK_VIDEO;
        }
    }
}
