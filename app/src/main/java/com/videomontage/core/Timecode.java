package com.videomontage.core;

import java.util.Locale;

public final class Timecode {
    private Timecode() {}

    /** mm:ss.ff — frames, not hundredths, because that's what editors read. */
    public static String format(long ms, float frameRate) {
        long totalSeconds = ms / 1000;
        long frames = (long) ((ms % 1000) / 1000f * frameRate);
        return String.format(Locale.US, "%02d:%02d.%02d",
                totalSeconds / 60, totalSeconds % 60, frames);
    }

    public static String formatShort(long ms) {
        long totalSeconds = ms / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
