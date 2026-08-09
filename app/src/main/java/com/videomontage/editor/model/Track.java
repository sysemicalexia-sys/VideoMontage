package com.videomontage.editor.model;

import com.videomontage.core.Ids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** One lane of the timeline. Clips are always position-sorted; the
 *  invariant lives here, not at every call site. */
public final class Track {

    public enum Kind { VIDEO, OVERLAY, TEXT, AUDIO, FX }

    public final String id;
    public final Kind kind;
    public final List<Clip> clips;
    public final Transition transition;
    public final boolean muted;
    public final boolean hidden;
    public final boolean locked;

    public Track(Kind kind) {
        this(Ids.newId(), kind, Collections.<Clip>emptyList(), Transition.none(),
                false, false, false);
    }

    private Track(String id, Kind kind, List<Clip> clips, Transition transition,
                  boolean muted, boolean hidden, boolean locked) {
        this.id = id;
        this.kind = kind;
        this.clips = Collections.unmodifiableList(clips);
        this.transition = transition;
        this.muted = muted;
        this.hidden = hidden;
        this.locked = locked;
    }

    public long endMs() {
        long end = 0;
        for (Clip c : clips) end = Math.max(end, c.timing.endMs());
        return end;
    }

    public Clip clipAt(long tMs) {
        for (Clip c : clips)
            if (tMs >= c.timing.positionMs && tMs < c.timing.endMs()) return c;
        return null;
    }

    public boolean overlaps(ClipTiming timing, String ignoreClipId) {
        for (Clip c : clips) {
            if (c.id.equals(ignoreClipId)) continue;
            if (timing.positionMs < c.timing.endMs() && c.timing.positionMs < timing.endMs())
                return true;
        }
        return false;
    }

    public Track inserted(Clip clip) {
        List<Clip> next = new ArrayList<>(clips);
        next.add(clip);
        sortByPosition(next);
        return new Track(id, kind, next, transition, muted, hidden, locked);
    }

    public Track removed(String clipId) {
        List<Clip> next = new ArrayList<>();
        for (Clip c : clips) if (!c.id.equals(clipId)) next.add(c);
        return new Track(id, kind, next, transition, muted, hidden, locked);
    }

    public Track replaced(Clip clip) {
        List<Clip> next = new ArrayList<>(clips.size());
        for (Clip c : clips) next.add(c.id.equals(clip.id) ? clip : c);
        sortByPosition(next);
        return new Track(id, kind, next, transition, muted, hidden, locked);
    }

    public Track withTransition(Transition t) {
        return new Track(id, kind, clips, t, muted, hidden, locked);
    }

    private static void sortByPosition(List<Clip> list) {
        Collections.sort(list, new Comparator<Clip>() {
            @Override public int compare(Clip a, Clip b) {
                return Long.compare(a.timing.positionMs, b.timing.positionMs);
            }
        });
    }
}
