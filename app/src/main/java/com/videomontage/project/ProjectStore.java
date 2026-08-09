package com.videomontage.project;

import com.videomontage.editor.model.AudioClip;
import com.videomontage.editor.model.Clip;
import com.videomontage.editor.model.ClipTiming;
import com.videomontage.editor.model.CropRect;
import com.videomontage.editor.model.Effect;
import com.videomontage.editor.model.ImageClip;
import com.videomontage.editor.model.Project;
import com.videomontage.editor.model.TextClip;
import com.videomontage.editor.model.Timeline;
import com.videomontage.editor.model.Track;
import com.videomontage.editor.model.Transform;
import com.videomontage.editor.model.Transition;
import com.videomontage.editor.model.VideoClip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JSON (de)serialization for projects. org.json only — no external
 *  dependency, which keeps the AIDE build self-contained. */
public final class ProjectStore {

    private ProjectStore() {}

    public static String toJson(Project project) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", 2);
            root.put("id", project.id);
            root.put("name", project.name);
            root.put("createdAt", project.createdAtMs);
            root.put("modifiedAt", project.modifiedAtMs);
            if (project.thumbnailPath != null) root.put("thumbnail", project.thumbnailPath);

            Timeline tl = project.timeline;
            JSONObject jTimeline = new JSONObject();
            jTimeline.put("width", tl.canvasWidth);
            jTimeline.put("height", tl.canvasHeight);
            jTimeline.put("frameRate", tl.frameRate);
            JSONArray jTracks = new JSONArray();
            for (Track track : tl.tracks) jTracks.put(trackToJson(track));
            jTimeline.put("tracks", jTracks);
            root.put("timeline", jTimeline);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("project serialization failed", e);
        }
    }

    private static JSONObject trackToJson(Track track) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", track.id);
        j.put("kind", track.kind.name());
        j.put("muted", track.muted);
        j.put("hidden", track.hidden);
        j.put("locked", track.locked);
        j.put("transition", track.transition.kind.name());
        j.put("transitionMs", track.transition.durationMs);
        JSONArray jClips = new JSONArray();
        for (Clip clip : track.clips) jClips.put(clipToJson(clip));
        j.put("clips", jClips);
        return j;
    }

    private static JSONObject clipToJson(Clip clip) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", clip.id);
        j.put("label", clip.label);
        j.put("volume", clip.volume);
        ClipTiming t = clip.timing;
        j.put("positionMs", t.positionMs);
        j.put("durationMs", t.durationMs);
        j.put("trimInMs", t.trimInMs);
        j.put("speed", t.speed);

        JSONObject jTransform = new JSONObject();
        Transform tr = clip.transform;
        jTransform.put("cx", tr.centerX);
        jTransform.put("cy", tr.centerY);
        jTransform.put("sx", tr.scaleX);
        jTransform.put("sy", tr.scaleY);
        jTransform.put("rot", tr.rotationDeg);
        jTransform.put("opacity", tr.opacity);
        j.put("transform", jTransform);

        if (clip instanceof VideoClip) {
            VideoClip v = (VideoClip) clip;
            j.put("type", "video");
            j.put("src", v.sourcePath);
            j.put("srcDurationMs", v.sourceDurationMs);
            j.put("embeddedAudio", v.hasEmbeddedAudio);
        } else if (clip instanceof ImageClip) {
            j.put("type", "image");
            j.put("src", ((ImageClip) clip).sourcePath);
        } else if (clip instanceof AudioClip) {
            AudioClip a = (AudioClip) clip;
            j.put("type", "audio");
            j.put("src", a.sourcePath);
            j.put("fadeInMs", a.fadeInMs);
            j.put("fadeOutMs", a.fadeOutMs);
            JSONArray peaks = new JSONArray();
            for (float p : a.waveformPeaks) peaks.put(p);
            j.put("peaks", peaks);
        } else if (clip instanceof TextClip) {
            TextClip tx = (TextClip) clip;
            j.put("type", "text");
            j.put("text", tx.text);
            j.put("font", tx.fontFamily);
            j.put("sizeSp", tx.sizeSp);
            j.put("color", tx.colorArgb);
        }

        JSONArray jEffects = new JSONArray();
        for (Effect e : clip.effects) {
            JSONObject je = new JSONObject();
            je.put("kind", e.kind.name());
            JSONObject jp = new JSONObject();
            for (Map.Entry<String, Float> en : e.params.entrySet())
                jp.put(en.getKey(), en.getValue());
            je.put("params", jp);
            je.put("rangeStart", e.rangeStartMs);
            je.put("rangeEnd", e.rangeEndMs);
            jEffects.put(je);
        }
        j.put("effects", jEffects);
        return j;
    }

    public static Project fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject jt = root.getJSONObject("timeline");
            List<Track> tracks = new ArrayList<>();
            JSONArray jTracks = jt.getJSONArray("tracks");
            for (int i = 0; i < jTracks.length(); i++) tracks.add(trackFromJson(jTracks.getJSONObject(i)));
            Timeline timeline = new Timeline(tracks, jt.getInt("width"),
                    jt.getInt("height"), (float) jt.getDouble("frameRate"));
            return new Project(root.getString("id"), root.getString("name"), timeline,
                    root.getLong("createdAt"), root.getLong("modifiedAt"),
                    root.optString("thumbnail", null));
        } catch (JSONException e) {
            throw new IllegalArgumentException("corrupt project file", e);
        }
    }

    private static Track trackFromJson(JSONObject j) throws JSONException {
        Track track = new Track(Track.Kind.valueOf(j.getString("kind")))
                .withTransition(new Transition(
                        Transition.Kind.valueOf(j.optString("transition", "NONE")),
                        j.optLong("transitionMs", 0)));
        JSONArray jClips = j.getJSONArray("clips");
        for (int i = 0; i < jClips.length(); i++)
            track = track.inserted(clipFromJson(jClips.getJSONObject(i)));
        return track;
    }

    private static Clip clipFromJson(JSONObject j) throws JSONException {
        ClipTiming timing = new ClipTiming(j.getLong("positionMs"), j.getLong("durationMs"),
                j.getLong("trimInMs"), (float) j.optDouble("speed", 1.0));
        JSONObject jtr = j.optJSONObject("transform");
        Transform transform = jtr == null ? Transform.identity()
                : new Transform((float) jtr.optDouble("cx", 0.5), (float) jtr.optDouble("cy", 0.5),
                        (float) jtr.optDouble("sx", 1), (float) jtr.optDouble("sy", 1),
                        (float) jtr.optDouble("rot", 0), CropRect.full(),
                        (float) jtr.optDouble("opacity", 1));
        List<Effect> effects = effectsFromJson(j.optJSONArray("effects"));
        String label = j.optString("label", "Clip");
        String type = j.getString("type");

        String id = j.getString("id");
        switch (type) {
            case "video":
                return new VideoClip(id, timing, transform, effects,
                        (float) j.optDouble("volume", 1), label, j.getString("src"),
                        j.optLong("srcDurationMs", 0), j.optBoolean("embeddedAudio", true));
            case "image":
                return new ImageClip(id, timing, transform, effects, label, j.getString("src"));
            case "audio": {
                JSONArray jp = j.optJSONArray("peaks");
                float[] peaks = new float[jp == null ? 0 : jp.length()];
                for (int i = 0; i < peaks.length; i++) peaks[i] = (float) jp.optDouble(i, 0);
                return new AudioClip(id, timing, (float) j.optDouble("volume", 1), label,
                        j.getString("src"), peaks,
                        j.optLong("fadeInMs", 0), j.optLong("fadeOutMs", 0));
            }
            default:
                return new TextClip(id, timing, transform, label, j.optString("text", ""),
                        j.optString("font", "sans-serif-medium"),
                        (float) j.optDouble("sizeSp", 42), j.optInt("color", 0xFFFFFFFF));
        }
    }

    private static List<Effect> effectsFromJson(JSONArray jEffects) throws JSONException {
        List<Effect> out = new ArrayList<>();
        if (jEffects == null) return out;
        for (int i = 0; i < jEffects.length(); i++) {
            JSONObject je = jEffects.getJSONObject(i);
            Map<String, Float> params = new HashMap<>();
            JSONObject jp = je.optJSONObject("params");
            if (jp != null) {
                java.util.Iterator<String> keys = jp.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    params.put(k, (float) jp.getDouble(k));
                }
            }
            out.add(new Effect(Effect.Kind.valueOf(je.getString("kind")), params,
                    java.util.Collections.<com.videomontage.editor.model.KeyframeTrack>emptyList(),
                    je.optLong("rangeStart", 0), je.optLong("rangeEnd", Long.MAX_VALUE)));
        }
        return out;
    }
}
