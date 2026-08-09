package com.videomontage.export;

import android.os.Handler;
import android.os.Looper;

import com.videomontage.editor.model.Timeline;
import com.videomontage.nativecore.LayerMarshaller;
import com.videomontage.nativecore.NativeEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Drives a full-timeline render into the encoder on a dedicated thread.
 *  Progress hops back to the main thread; cancel is cooperative. */
public final class ExportManager {

    public interface Callback {
        void onProgress(float fraction);
        void onFinished(String outputPath);
        void onFailed(String reason);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    public void export(final Timeline timeline, final String outputPath, final Callback callback) {
        cancelled = false;
        executor.execute(new Runnable() {
            @Override public void run() {
                runExport(timeline, outputPath, callback);
            }
        });
    }

    public void cancel() {
        cancelled = true;
        if (NativeEngine.ensureLoaded()) NativeEngine.nativeCancelExport();
    }

    private void runExport(final Timeline timeline, final String outputPath, final Callback callback) {
        final LayerMarshaller marshaller = new LayerMarshaller();
        int w = timeline.canvasWidth, h = timeline.canvasHeight;
        final int fps = Math.round(timeline.frameRate);
        if (!NativeEngine.ensureLoaded()
                || !NativeEngine.nativeInit(w, h)
                || !NativeEngine.nativeStartExport(outputPath, w, h, fps)) {
            postFailed(callback, "encoder unavailable");
            return;
        }

        long frameMs = 1000L / fps;
        long durationMs = timeline.durationMs();
        boolean ok = true;
        for (long t = 0; t <= durationMs && !cancelled; t += frameMs) {
            marshaller.marshal(timeline, t);
            if (!marshaller.exportAt(t)) {
                ok = false;
                break;
            }
            final float p = durationMs == 0 ? 1f : (float) t / durationMs;
            main.post(new Runnable() {
                @Override public void run() { callback.onProgress(p); }
            });
        }

        final boolean finished = ok && !cancelled && NativeEngine.nativeFinishExport();
        main.post(new Runnable() {
            @Override public void run() {
                if (finished) callback.onFinished(outputPath);
                else callback.onFailed("export cancelled or encoder error");
            }
        });
    }

    private void postFailed(final Callback callback, final String reason) {
        main.post(new Runnable() {
            @Override public void run() { callback.onFailed(reason); }
        });
    }
}
