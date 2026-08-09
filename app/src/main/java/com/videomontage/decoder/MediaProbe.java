package com.videomontage.decoder;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

/** Lightweight source inspection for the import flow — duration,
 *  dimensions, thumbnails. Decoding for playback never goes through here;
 *  that's the native pipeline's job. */
public final class MediaProbe {

    public static final class Info {
        public long durationMs;
        public int width;
        public int height;
        public boolean hasAudio;
    }

    private MediaProbe() {}

    public static Info inspect(String path) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        Info info = new Info();
        try {
            r.setDataSource(path);
            info.durationMs = parseLong(r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            info.width = (int) parseLong(r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            info.height = (int) parseLong(r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            info.hasAudio = "yes".equals(r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
        } catch (RuntimeException e) {
            info.durationMs = 0;
        } finally {
            r.release();
        }
        return info;
    }

    /** Frame at `timeMs`, downscaled — used for project thumbnails. */
    public static Bitmap thumbnail(String path, long timeMs, int maxEdge) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            Bitmap frame = r.getFrameAtTime(timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            float scale = Math.min(1f,
                    maxEdge / (float) Math.max(frame.getWidth(), frame.getHeight()));
            if (scale >= 1f) return frame;
            Bitmap scaled = Bitmap.createScaledBitmap(frame,
                    Math.round(frame.getWidth() * scale),
                    Math.round(frame.getHeight() * scale), true);
            if (scaled != frame) frame.recycle();
            return scaled;
        } catch (RuntimeException e) {
            return null;
        } finally {
            r.release();
        }
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }
}
