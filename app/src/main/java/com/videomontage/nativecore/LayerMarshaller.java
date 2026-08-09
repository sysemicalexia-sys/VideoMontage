package com.videomontage.nativecore;

import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.Effect;
import com.videomontage.editor.model.ImageClip;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.model.VideoClip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Flattens the immutable timeline at one instant into the parallel-array
 *  wire format the native compositor consumes. Reuses its buffers across
 *  frames — this runs at up to 60 Hz and must not allocate per frame. */
public final class LayerMarshaller {

    private static final int MAX_LAYERS = 32;

    private final String[] paths = new String[MAX_LAYERS];
    private final int[] clipKeys = new int[MAX_LAYERS];
    private final long[] sourcePtsUs = new long[MAX_LAYERS];
    private final float[] mvps = new float[MAX_LAYERS * 16];
    private final float[] opacities = new float[MAX_LAYERS];
    private final float[] effects = new float[MAX_LAYERS * 8 * 5];
    private final int[] effectCounts = new int[MAX_LAYERS];
    private int layerCount;

    private final Map<String, Integer> clipKeyByClipId = new HashMap<>();
    private int nextClipKey;

    public int layerCount() { return layerCount; }

    /** Rebuilds the layer list for `tMs`. Returns false when nothing is
     *  visible (the renderer clears to canvas color and is done). */
    public boolean marshal(Timeline timeline, long tMs) {
        layerCount = 0;
        for (Track track : timeline.tracks) {
            if (track.kind == Track.Kind.AUDIO || track.hidden) continue;
            Clip clip = track.clipAt(tMs);
            if (clip == null || !clip.isVisual()) continue;
            if (layerCount >= MAX_LAYERS) break;
            addLayer(clip, clip.timing.sourceTimeAt(tMs));
        }
        return layerCount > 0;
    }

    private void addLayer(Clip clip, long sourceMs) {
        final int i = layerCount++;
        clipKeys[i] = keyFor(clip.id);
        paths[i] = sourcePathOf(clip);
        sourcePtsUs[i] = sourceMs * 1000L;
        float[] mvp = clip.transform.toMvp();
        System.arraycopy(mvp, 0, mvps, i * 16, 16);
        opacities[i] = clip.transform.opacity;

        int effectBase = i * 8 * 5;
        int count = 0;
        for (Effect e : clip.effects) {
            if (count >= 8) break;
            int o = effectBase + count * 5;
            effects[o] = e.kind.shaderId;
            effects[o + 1] = param(e, "exposure", "amount", 0f);
            effects[o + 2] = param(e, "contrast", "amount", 1f);
            effects[o + 3] = param(e, "saturation", "amount", 1f);
            effects[o + 4] = param(e, "temperature", "radius", 0f);
            count++;
        }
        effectCounts[i] = count;
    }

    private static float param(Effect e, String named, String fallback, float def) {
        Float v = e.params.get(named);
        if (v != null) return v;
        v = e.params.get(fallback);
        return v != null ? v : def;
    }

    private static String sourcePathOf(Clip clip) {
        if (clip instanceof VideoClip) return ((VideoClip) clip).sourcePath;
        if (clip instanceof ImageClip) return ((ImageClip) clip).sourcePath;
        return "";
    }

    private int keyFor(String clipId) {
        Integer key = clipKeyByClipId.get(clipId);
        if (key == null) {
            key = nextClipKey++;
            clipKeyByClipId.put(clipId, key);
        }
        return key;
    }

    /** Forgets decoder slot assignments — call when clips are removed. */
    public void prune(Timeline timeline) {
        clipKeyByClipId.keySet().retainAll(clipIds(timeline));
    }

    private static List<String> clipIds(Timeline timeline) {
        List<String> ids = new ArrayList<>();
        for (Track t : timeline.tracks)
            for (Clip c : t.clips) ids.add(c.id);
        return ids;
    }

    /** Same payload as renderAt, routed to the export encoder surface. */
    public boolean exportAt(long timelineMs) {
        if (layerCount == 0) {
            return NativeEngine.nativeExportFrame(
                    new String[0], new int[0], new long[0],
                    new float[0], new float[0], null, null, timelineMs * 1000L);
        }
        return NativeEngine.nativeExportFrame(
                slice(paths, layerCount), slice(clipKeys, layerCount),
                slice(sourcePtsUs, layerCount), slice(mvps, layerCount * 16),
                slice(opacities, layerCount),
                slice(effects, sum(effectCounts, layerCount) * 5),
                slice(effectCounts, layerCount), timelineMs * 1000L);
    }

    public boolean renderAt(long timelineMs) {
        if (layerCount == 0) {
            return NativeEngine.nativeRenderAt(
                    new String[0], new int[0], new long[0],
                    new float[0], new float[0], null, null, timelineMs * 1000L);
        }
        return NativeEngine.nativeRenderAt(
                slice(paths, layerCount), slice(clipKeys, layerCount),
                slice(sourcePtsUs, layerCount), slice(mvps, layerCount * 16),
                slice(opacities, layerCount),
                slice(effects, sum(effectCounts, layerCount) * 5),
                slice(effectCounts, layerCount), timelineMs * 1000L);
    }

    private static int sum(int[] a, int n) {
        int s = 0;
        for (int i = 0; i < n; i++) s += a[i];
        return s;
    }

    // Slices are the one allocation per frame; kept small and short-lived.
    private static String[] slice(String[] a, int n) {
        String[] out = new String[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }

    private static int[] slice(int[] a, int n) {
        int[] out = new int[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }

    private static long[] slice(long[] a, int n) {
        long[] out = new long[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }

    private static float[] slice(float[] a, int n) {
        float[] out = new float[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }
}
