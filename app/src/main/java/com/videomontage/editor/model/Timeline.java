package com.videomontage.editor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Timeline {
    public final List<Track> tracks;
    public final int canvasWidth;
    public final int canvasHeight;
    public final float frameRate;

    public Timeline(List<Track> tracks, int canvasWidth, int canvasHeight, float frameRate) {
        this.tracks = Collections.unmodifiableList(tracks);
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.frameRate = frameRate;
    }

    public static Timeline empty() {
        return new Timeline(Collections.<Track>emptyList(), 1920, 1080, 30f);
    }

    public long durationMs() {
        long d = 0;
        for (Track t : tracks) d = Math.max(d, t.endMs());
        return d;
    }

    public long frameDurationMs() {
        return (long) (1000f / frameRate);
    }

    public Track track(String trackId) {
        for (Track t : tracks) if (t.id.equals(trackId)) return t;
        return null;
    }

    public Track trackOfClip(String clipId) {
        for (Track t : tracks)
            for (Clip c : t.clips) if (c.id.equals(clipId)) return t;
        return null;
    }

    public Clip clip(String clipId) {
        for (Track t : tracks)
            for (Clip c : t.clips) if (c.id.equals(clipId)) return c;
        return null;
    }

    public Timeline updatedTrack(String trackId, Track updated) {
        List<Track> next = new ArrayList<>(tracks.size());
        for (Track t : tracks) next.add(t.id.equals(trackId) ? updated : t);
        return new Timeline(next, canvasWidth, canvasHeight, frameRate);
    }

    public Timeline addedTrack(Track track) {
        List<Track> next = new ArrayList<>(tracks);
        next.add(track);
        return new Timeline(next, canvasWidth, canvasHeight, frameRate);
    }

    /** Undo snapshots — clips are immutable, so a track-list copy is
     *  exactly as deep as needed. */
    public Timeline snapshot() {
        return new Timeline(new ArrayList<>(tracks), canvasWidth, canvasHeight, frameRate);
    }
}
