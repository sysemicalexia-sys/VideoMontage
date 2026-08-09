package com.videomontage.nativecore;

import android.util.Log;
import android.view.Surface;

/** JNI facade — the only Java class that talks to montage_engine directly.
 *  All arrays use the wire format documented in jni_bridge.cpp: parallel
 *  arrays, effects flattened as [kind, p0..p3] runs.
 *
 *  The native library is loaded lazily via ensureLoaded() and a failure is
 *  NEVER fatal: AIDE builds have been seen to drop libc++_shared.so (or the
 *  engine .so itself) from the APK, and an UnsatisfiedLinkError on the GL
 *  thread used to kill the whole app the moment the editor opened. Now the
 *  caller degrades to a dark preview + a Toast naming the missing library. */
public final class NativeEngine {

    private static final String TAG = "NativeEngine";
    private static boolean loaded;
    private static String loadError;

    private NativeEngine() {}

    /** Returns true when the native engine is ready to use. Safe to call
     *  from any thread; the load attempt happens at most once. */
    public static synchronized boolean ensureLoaded() {
        if (loaded || loadError != null) return loaded;
        try {
            // With APP_STL=c++_static (recommended for AIDE) there is no
            // separate STL library — try, but never require it.
            try {
                System.loadLibrary("c++_shared");
            } catch (UnsatisfiedLinkError ignored) {
                // static STL — nothing extra to load
            }
            System.loadLibrary("montage_engine");
            loaded = true;
        } catch (Throwable e) {
            loadError = e.getMessage();
            Log.e(TAG, "native library failed to load: " + loadError);
        }
        return loaded;
    }

    /** Human-readable reason ensureLoaded() failed, or null. Surfaced in the
     *  editor so the cause is visible on-device without logcat. */
    public static synchronized String loadErrorMessage() {
        return loadError;
    }

    public static native boolean nativeInit(int canvasWidth, int canvasHeight);
    public static native boolean nativeAttachPreview(Surface surface);
    public static native void nativeDetachPreview();

    public static native boolean nativeRenderAt(
            String[] paths, int[] clipKeys, long[] sourcePtsUs,
            float[] mvps, float[] opacities,
            float[] effects, int[] effectCounts, long timelinePtsUs);

    public static native void nativeInvalidate();

    public static native boolean nativeStartExport(String outputPath, int width, int height, int frameRate);
    public static native boolean nativeExportFrame(
            String[] paths, int[] clipKeys, long[] sourcePtsUs,
            float[] mvps, float[] opacities,
            float[] effects, int[] effectCounts, long ptsUs);
    public static native boolean nativeFinishExport();
    public static native void nativeCancelExport();
    public static native float nativeExportProgress();
    public static native void nativeShutdown();
}
