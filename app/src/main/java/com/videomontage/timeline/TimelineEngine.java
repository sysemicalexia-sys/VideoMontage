package com.videomontage.timeline;

import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.Effect;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.ops.TimelineOps;
import com.videomontage.editor.ops.UndoStack;

import java.util.ArrayList;
import java.util.List;

/** Single writer for the timeline. UI gestures call intent methods here;
 *  the engine applies ops, records undo, publishes immutable state, and
 *  tells the render layer to invalidate its frame cache. */
public final class TimelineEngine {

    public interface Listener {
        void onTimelineChanged(Timeline timeline);
        void onSelectionChanged(String clipId);
    }

    private Timeline timeline;
    private final UndoStack undoStack = new UndoStack(64);
    private final List<Listener> listeners = new ArrayList<>();
    private String selectedClipId;
    private long playheadMs;

    public TimelineEngine(Timeline initial) {
        this.timeline = initial;
    }

    public Timeline timeline() { return timeline; }
    public String selectedClipId() { return selectedClipId; }
    public long playheadMs() { return playheadMs; }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void select(String clipId) {
        selectedClipId = clipId;
        for (Listener l : listeners) l.onSelectionChanged(clipId);
    }

    public void setPlayhead(long tMs) {
        playheadMs = Math.max(0, tMs);
    }

    public void addTrack(com.videomontage.editor.model.Track track) {
        mutate(timeline.addedTrack(track));
    }

    public void insertClip(String trackId, Clip clip) {
        mutate(TimelineOps.insert(timeline, trackId, clip));
    }

    public void moveClip(String clipId, long toPositionMs, String toTrackId, float pxPerMs) {
        MagneticSnapper.Result snapped =
                new MagneticSnapper(48f).snap(toPositionMs, timeline, playheadMs, pxPerMs);
        mutate(TimelineOps.move(timeline, clipId, snapped.positionMs, toTrackId));
    }

    public void trimClip(String clipId, TimelineOps.Edge edge, long deltaMs) {
        mutate(TimelineOps.trim(timeline, clipId, edge, deltaMs));
    }

    public void splitAtPlayhead(String clipId) {
        mutate(TimelineOps.split(timeline, clipId, playheadMs));
    }

    public void removeClip(String clipId, boolean ripple) {
        mutate(TimelineOps.remove(timeline, clipId, ripple));
        if (clipId.equals(selectedClipId)) select(null);
    }

    public void setSpeed(String clipId, float speed) {
        Clip clip = timeline.clip(clipId);
        if (clip == null) return;
        float clamped = Math.max(0.1f, Math.min(10f, speed));
        mutate(replaceClip(clipId, clip.withTiming(clip.timing.withSpeed(clamped))));
    }

    public void applyEffect(String clipId, Effect effect) {
        Clip clip = timeline.clip(clipId);
        if (clip == null) return;
        List<Effect> next = new ArrayList<>(clip.effects);
        next.add(effect);
        mutate(replaceClip(clipId, clip.withEffects(next)));
    }

    public void undo() {
        Timeline prev = undoStack.undo(timeline);
        if (prev != null) publish(prev);
    }

    public void redo() {
        Timeline next = undoStack.redo(timeline);
        if (next != null) publish(next);
    }

    private Timeline replaceClip(String clipId, Clip updated) {
        com.videomontage.editor.model.Track track = timeline.trackOfClip(clipId);
        if (track == null) return timeline;
        return timeline.updatedTrack(track.id, track.replaced(updated));
    }

    private void mutate(Timeline next) {
        if (next == timeline) return;
        undoStack.record(timeline);
        publish(next);
    }

    private void publish(Timeline next) {
        timeline = next;
        for (Listener l : listeners) l.onTimelineChanged(next);
    }
}
