# VideoMontage

A professional-grade Android video editor — dark cinematic UI, custom timeline
engine, and a native C++ rendering pipeline. Built for on-device compilation
with **AIDE** (arm64-v8a, ndk-build), and equally at home in Android Studio.

## Architecture

Single Gradle module (AIDE-compatible), strict package modularity — every
package has one responsibility and no package reaches sideways:

```
com.videomontage
├── app          Application shell, manifest entry points
├── core         Ids, math, timecode — zero Android dependencies
├── editor
│   ├── model    Immutable Project / Timeline / Track / Clip hierarchy,
│   │            ClipTiming, Transform, KeyframeTrack, Effect, Transition
│   └── ops      Pure timeline algebra (insert/move/trim/split/ripple) + UndoStack
├── timeline     TimelineEngine (single writer), MagneticSnapper, PlaybackClock
├── nativecore   NativeEngine (JNI facade), LayerMarshaller (timeline → wire arrays)
├── render       RenderCoordinator — per-frame render decisions
├── decoder      MediaProbe (import inspection), WaveformExtractor
├── effects      EffectLibrary — curated presets, same pipeline as custom effects
├── audio        PreviewAudioPlayer — decoded PCM preview mix
├── playback     PlaybackController — clock ↔ audio lockstep
├── preview      PreviewController — playback ↔ rendering glue
├── export       ExportManager — full-timeline encode driver
├── project      ProjectStore (JSON), ProjectRepository (atomic file writes)
├── storage      StoragePaths, MediaImporter (content:// → app-private file)
├── theme        Colors — Java mirror of res/values/colors.xml
├── widgets      TimelineView (custom-drawn, 60fps), PreviewView (GL + gestures)
├── ui
│   ├── home     HomeActivity — projects grid, ambient backdrop
│   └── editor   EditorActivity — preview-first editing surface
└── utils        Spring (critically-damped integrator), ViewFx (motion vocabulary)
```

### Rendering pipeline (native, `app/src/main/jni`)

```
Decoder (MediaCodec) → FramePool (fixed arena, zero hot-path alloc)
    → FrameCache (LRU, generation-tagged) → Renderer (GLES3, ping-pong FBOs)
    → Compositor (per-layer effect chains) → Preview surface / Encoder input
```

JNI only marshals — all decisions live in C++ (`engine.cpp` is the root).
Export renders straight into the encoder's input surface; pixels never
round-trip to the CPU.

## Build (AIDE)

1. Open the `VideoMontage` folder as a project in AIDE.
2. Ensure the NDK is installed — `app/build.gradle` wires
   `externalNativeBuild.ndkBuild` to `app/src/main/jni/Android.mk`.
3. Run. ABI is locked to `arm64-v8a`; `minSdk 26`, `target 29`, `compileSdk 36`.

**Zero external dependencies.** The app is framework-only — no appcompat,
no recyclerview, nothing to download or merge. This is deliberate: AIDE's
bundled aapt fails merging modern androidx resources ("Found tag id where
item is expected"), so the UI uses `Activity`, `GridView`, framework
`AlertDialog`, and `android:Theme.Material.NoActionBar` as the base theme.

## Editing model

- Clips are immutable; every edit produces new instances, and undo is
  snapshot-based (`UndoStack`, 64 deep).
- `TimelineEngine` is the single writer — gestures express intent, the
  engine applies ops, records undo, publishes state, and invalidates the
  native frame cache.
- Snapping is zoom-aware (`MagneticSnapper`): the pull distance is defined
  in pixels, so it feels identical at any zoom level.

## Motion & input

- Three independent motion systems in the timeline: pinch zoom rides a
  spring, the playhead rides a spring, scrolling rides an OverScroller.
  Each owns exactly one property, so they never fight.
- Custom views allocate nothing on the per-frame path (one deliberate
  gradient shader per visible clip — the depth cue is worth it).
