package com.videomontage.editor.ops;

import com.videomontage.core.Mathx;
import com.videomontage.editor.model.AudioClip;
import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.ClipTiming;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.model.VideoClip;

/** Pure timeline algebra. Every mutation returns a new Timeline; the
 *  engine sequences these with snapping and undo. No Android imports —
 *  fully unit-testable on the JVM. */
public final class TimelineOps {

    public enum Edge { START, END }

    public static final long MIN_CLIP_MS = 100L;

    private TimelineOps() {}

    public static Timeline insert(Timeline timeline, String trackId, Clip clip) {
        Track track = timeline.track(trackId);
        if (track == null) return timeline;
        Clip resolved = resolveCollision(track, clip);
        return timeline.updatedTrack(trackId, track.inserted(resolved));
    }

    public static Timeline move(Timeline timeline, String clipId, long toPositionMs,
                                String toTrackId) {
        Track from = timeline.trackOfClip(clipId);
        Clip clip = timeline.clip(clipId);
        if (from == null || clip == null) return timeline;
        Clip moved = clip.withTiming(clip.timing.moved(Math.max(0, toPositionMs)));
        Timeline without = timeline.updatedTrack(from.id, from.removed(clipId));
        String target = toTrackId != null ? toTrackId : from.id;
        return insert(without, target, moved);
    }

    /** Trim one edge, honoring source bounds and neighbors. */
    public static Timeline trim(Timeline timeline, String clipId, Edge edge, long deltaMs) {
        Track track = timeline.trackOfClip(clipId);
        Clip clip = timeline.clip(clipId);
        if (track == null || clip == null) return timeline;
        ClipTiming t = clip.timing;
        ClipTiming next;
        if (edge == Edge.START) {
            long maxDelta = Math.max(0, t.durationMs - MIN_CLIP_MS);
            long d = Mathx.clampLong(deltaMs, -t.trimInMs, maxDelta);
            next = t.withStart(t.positionMs + d, t.durationMs - d,
                    t.trimInMs + (long) (d * t.speed));
        } else {
            Long sourceLimit = sourceLimitMs(clip);
            long hi = sourceLimit == null ? Long.MAX_VALUE
                    : (long) ((sourceLimit - t.trimInMs) / t.speed - t.durationMs);
            long d = Mathx.clampLong(deltaMs, -(t.durationMs - MIN_CLIP_MS), hi);
            next = t.withDuration(t.durationMs + d);
        }
        if (track.overlaps(next, clipId)) return timeline;
        return timeline.updatedTrack(track.id, track.replaced(clip.withTiming(next)));
    }

    /** Split at a timeline instant; both halves keep transform/effects. */
    public static Timeline split(Timeline timeline, String clipId, long atMs) {
        Track track = timeline.trackOfClip(clipId);
        Clip clip = timeline.clip(clipId);
        if (track == null || clip == null) return timeline;
        ClipTiming t = clip.timing;
        if (atMs <= t.positionMs || atMs >= t.endMs()) return timeline;
        long firstDur = atMs - t.positionMs;
        Clip left = clip.withTiming(t.withDuration(firstDur));
        Clip right = clip.withTiming(t.withStart(atMs, t.endMs() - atMs,
                t.trimInMs + (long) (firstDur * t.speed))).withNewId();
        return timeline.updatedTrack(track.id, track.replaced(left).inserted(right));
    }

    public static Timeline remove(Timeline timeline, String clipId, boolean ripple) {
        Track track = timeline.trackOfClip(clipId);
        Clip clip = timeline.clip(clipId);
        if (track == null || clip == null) return timeline;
        Track removed = track.removed(clipId);
        if (ripple) {
            long gap = clip.timing.durationMs;
            Track rippled = removed;
            for (Clip c : removed.clips) {
                if (c.timing.positionMs >= clip.timing.endMs()) {
                    rippled = rippled.replaced(
                            c.withTiming(c.timing.moved(c.timing.positionMs - gap)));
                }
            }
            removed = rippled;
        }
        return timeline.updatedTrack(track.id, removed);
    }

    private static Clip resolveCollision(Track track, Clip clip) {
        if (!track.overlaps(clip.timing, clip.id)) return clip;
        long placeAfter = 0;
        for (Clip c : track.clips) {
            if (!c.id.equals(clip.id)) placeAfter = Math.max(placeAfter, c.timing.endMs());
        }
        return clip.withTiming(clip.timing.moved(placeAfter));
    }

    private static Long sourceLimitMs(Clip clip) {
        if (clip instanceof VideoClip) return ((VideoClip) clip).sourceDurationMs;
        if (clip instanceof AudioClip) return null; // audio sources loop-trim freely
        return null;
    }
}
