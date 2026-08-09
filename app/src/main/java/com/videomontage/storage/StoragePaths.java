package com.videomontage.storage;

import android.content.Context;

import java.io.File;

/** All app-owned directories in one place. Everything lives under the
 *  app-private root — no storage permission needed on the target SDK. */
public final class StoragePaths {

    private StoragePaths() {}

    public static File projectsDir(Context context) {
        return ensure(new File(context.getFilesDir(), "projects"));
    }

    public static File thumbnailsDir(Context context) {
        return ensure(new File(context.getFilesDir(), "thumbnails"));
    }

    public static File exportsDir(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext == null) ext = context.getFilesDir();
        return ensure(new File(ext, "exports"));
    }

    public static File importsDir(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext == null) ext = context.getFilesDir();
        return ensure(new File(ext, "imports"));
    }

    private static File ensure(File dir) {
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
