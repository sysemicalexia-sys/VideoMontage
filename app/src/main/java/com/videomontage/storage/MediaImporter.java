package com.videomontage.storage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Copies a picked content:// Uri into app-private storage so decoders
 *  always see a plain file path — the native pipeline never deals with
 *  content providers or permission lifetimes. */
public final class MediaImporter {

    public static final class Result {
        public final String path;
        public final String displayName;
        public final boolean isVideo;
        public final boolean isImage;

        Result(String path, String displayName, boolean isVideo, boolean isImage) {
            this.path = path;
            this.displayName = displayName;
            this.isVideo = isVideo;
            this.isImage = isImage;
        }
    }

    private MediaImporter() {}

    public static Result importUri(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        String mime = context.getContentResolver().getType(uri);
        boolean isVideo = mime != null && mime.startsWith("video/");
        boolean isAudio = mime != null && mime.startsWith("audio/");
        boolean isImage = mime != null && mime.startsWith("image/");
        String ext = extensionOf(name, isVideo ? ".mp4" : isAudio ? ".aac"
                : isImage ? ".jpg" : ".bin");

        File out = new File(StoragePaths.importsDir(context),
                System.currentTimeMillis() + "_" + sanitize(name) + ext);
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("cannot open " + uri);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        fos.flush();
        fos.close();
        in.close();
        return new Result(out.getAbsolutePath(), name, isVideo, isImage);
    }

    private static String displayName(Context context, Uri uri) {
        Cursor c = context.getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) return c.getString(idx);
            } finally {
                c.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "media";
    }

    private static String extensionOf(String name, String fallback) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : fallback;
    }

    private static String sanitize(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        return base.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
