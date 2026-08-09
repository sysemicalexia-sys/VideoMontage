package com.videomontage.timeline;

import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;

import java.util.ArrayList;
import java.util.List;

/** Pulls a dragged edge toward the nearest snap point (playhead, clip
 *  edges, timeline start) within a threshold that scales with zoom — the
 *  pull feels constant on screen regardless of pxPerMs. */
public final class MagneticSnapper {

    public static final class Result {
        public final long positionMs;
        /** Non-null when a snap engaged; the view draws a guide line here. */
        public final Long snappedToMs;

        Result(long positionMs, Long snappedToMs) {
            this.positionMs = positionMs;
            this.snappedToMs = snappedToMs;
        }
    }

    private final float thresholdPx;

    public MagneticSnapper(float thresholdPx) {
        this.thresholdPx = thresholdPx;
    }

    public Result snap(long candidateMs, Timeline timeline, long playheadMs, float pxPerMs) {
        float thresholdMs = thresholdPx / Math.max(pxPerMs, 0.001f);
        List<Long> points = new ArrayList<>();
        points.add(0L);
        points.add(playheadMs);
        for (Track track : timeline.tracks) {
            for (Clip c : track.clips) {
                points.add(c.timing.positionMs);
                points.add(c.timing.endMs());
            }
        }
        long nearest = candidateMs;
        long best = Long.MAX_VALUE;
        for (long p : points) {
            long d = Math.abs(p - candidateMs);
            if (d < best) { best = d; nearest = p; }
        }
        if (best <= thresholdMs) return new Result(nearest, nearest);
        return new Result(candidateMs, null);
    }
}
