package com.videomontage.editor.model;

import com.videomontage.core.Ids;

public final class Project {
    public final String id;
    public final String name;
    public final Timeline timeline;
    public final long createdAtMs;
    public final long modifiedAtMs;
    public final String thumbnailPath;

    public Project(String name, Timeline timeline) {
        this(Ids.newId(), name, timeline, System.currentTimeMillis(),
                System.currentTimeMillis(), null);
    }

    public Project(String id, String name, Timeline timeline,
                   long createdAtMs, long modifiedAtMs, String thumbnailPath) {
        this.id = id;
        this.name = name;
        this.timeline = timeline;
        this.createdAtMs = createdAtMs;
        this.modifiedAtMs = modifiedAtMs;
        this.thumbnailPath = thumbnailPath;
    }

    public Project touched(Timeline next) {
        return new Project(id, name, next, createdAtMs, System.currentTimeMillis(), thumbnailPath);
    }

    public Project withThumbnail(String path) {
        return new Project(id, name, timeline, createdAtMs, modifiedAtMs, path);
    }
}
