package com.videomontage.editor.ops;

import com.videomontage.editor.model.Timeline;

import java.util.ArrayDeque;
import java.util.Deque;

/** Snapshot undo. Timelines are immutable and clips are shared between
 *  snapshots, so a full snapshot is cheap — no command objects needed. */
public final class UndoStack {
    private final int capacity;
    private final Deque<Timeline> undo = new ArrayDeque<>();
    private final Deque<Timeline> redo = new ArrayDeque<>();

    public UndoStack(int capacity) {
        this.capacity = capacity;
    }

    public void record(Timeline state) {
        undo.addLast(state.snapshot());
        while (undo.size() > capacity) undo.removeFirst();
        redo.clear();
    }

    public Timeline undo(Timeline current) {
        Timeline prev = undo.pollLast();
        if (prev == null) return null;
        redo.addLast(current.snapshot());
        return prev;
    }

    public Timeline redo(Timeline current) {
        Timeline next = redo.pollLast();
        if (next == null) return null;
        undo.addLast(current.snapshot());
        return next;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
}
