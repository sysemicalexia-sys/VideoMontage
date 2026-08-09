# Crash fixes — round 2: launch, then "New Project"

## Round 2a — app died instantly at launch (FIXED by you)

The manifest used relative class names; `AndroidManifest.xml` in this tree
uses FQCNs everywhere (`com.videomontage.app.MontageApp`,
`com.videomontage.ui.home.HomeActivity`,
`com.videomontage.ui.editor.EditorActivity`). Note its theme is now the
framework theme `@android:style/Theme.Material.NoActionBar` so it has zero
resource dependencies — if you have your own `Theme.Montage` in
`res/values/themes.xml`, point `android:theme` back at it.

## Round 2b — crash on "New Project" (this fix)

Opening the editor is the **first moment the native library loads**. If
`libmontage_engine.so` (or its `libc++_shared.so` dependency) isn't inside
the APK, the GL thread died with `UnsatisfiedLinkError` and took the app
with it.

Changes:
- `nativecore/NativeEngine.java` — library load is now lazy and non-fatal
  (`ensureLoaded()`); a failure is remembered, never thrown.
- `render/RenderCoordinator.java`, `export/ExportManager.java` — every
  native entry point is guarded; preview just stays dark if the engine is
  absent.
- `ui/editor/EditorActivity.java` — shows a Toast with the exact dlopen
  error (e.g. `library "libc++_shared.so" not found`) so you can read the
  cause on-device, no logcat needed. The editor itself keeps working
  (timeline, import, save); preview/export stay off until the .so loads.

**To actually get the engine running** (not just survive its absence):
1. `app/src/main/jni/Application.mk` → `APP_STL := c++_static`
   (removes the `libc++_shared.so` dependency entirely).
2. Confirm `app/build.gradle` really wires the NDK build:
   `externalNativeBuild { ndkBuild { path 'src/main/jni/Android.mk' } }`
   and `abiFilters 'arm64-v8a'`.
3. After building, the APK must contain `lib/arm64-v8a/libmontage_engine.so`.

## If it STILL crashes on "New Project"

Then the cause is the editor layout (`res/layout/activity_editor.xml`),
which I can't see. Check these against the file:

- Custom views must be FQCN-tagged:
  `<com.videomontage.widgets.PreviewView ... />` and
  `<com.videomontage.widgets.TimelineView ... />` — any other class name
  (or an old package) throws `InflateException` at `setContentView`.
- These ids must exist (the code looks every one up):
  `preview, timeline, timecode, durationLabel, transport, playPause,
   projectName, addClip, split, undo, redo, exportButton, backButton`.
  A missing id is an immediate `NullPointerException`.
- `R.anim.enter_slide_up` and `R.anim.exit_fade` must exist — they're used
  the moment you tap "New Project".

The exact answer is always in AIDE's Logcat: run the app, reproduce the
crash, then look for `FATAL EXCEPTION` / `AndroidRuntime` — paste me that
stack trace and I'll fix the precise line.

---

# Round 1 — native crash inside the editor (already applied)

## The crash

The app compiled but died the moment the editor opened. Three layered causes,
all in the native preview path:

1. **Two EGL surfaces on one window (fatal).** `PreviewView` is a
   `GLSurfaceView` — it already owns an EGL context and a window surface,
   current on its own GL thread. `Engine::attachPreview()` then called
   `eglCreateWindowSurface()` on the **same** `ANativeWindow`, which is
   `EGL_BAD_ALLOC` per the EGL spec — and on real ARM drivers this, plus
   `eglMakeCurrent()` from a second context on GLSurfaceView's thread,
   corrupts driver state and kills the process (SIGSEGV/SIGABRT in
   libEGL/libGLES). This is the crash you saw.

2. **`System.loadLibrary("c++_shared")` could kill the editor instantly.**
   If AIDE's build doesn't bundle `libc++_shared.so` into the APK, the first
   touch of `NativeEngine` throws `UnsatisfiedLinkError` on the GL thread.

3. **GL calls with no current context on the export thread.**
   `ExportManager` called `nativeInit()` on its own thread, where nothing is
   current — shader/FBO creation there is undefined. Also, GL objects built
   in GLSurfaceView's context were reused in the encoder's context (contexts
   were not shared), so export could never have worked.

## The fixes (already applied in this tree)

**Native — `app/src/main/jni/`:**

- `engine.h/.cpp` — preview no longer creates any EGL objects. The
  GLSurfaceView GL thread renders into *its own* current context and
  GLSurfaceView itself swaps buffers. Native EGL is now used **only** for
  export (the encoder's input surface, owned by nobody else). `exportFrame`
  now takes the GL mutex.
- `compositor.h/.cpp` — the `Renderer` (programs, FBOs, upload texture) is
  built **lazily per EGL context**: `init()` only records canvas size (safe
  on any thread); the first frame under a given current context builds the
  GL objects for that context. Preview and export each get valid objects.
  Also: per-decoder seek tracking (backward scrub now seeks instead of
  freezing on a future frame), cache keys include the clip id (clips no
  longer collide on shared source PTS), failed decoder opens are stored
  instead of leaking an extractor every frame.
- `vm_decoder.h/.cpp` — MediaCodec ByteBuffer output is **YUV 4:2:0**, but
  it was memcpy'd and uploaded as `GL_RGBA` (garbage video). Now converted
  to RGBA (BT.601 video-range) while copying into the pool, with stride /
  slice-height / color-format handling (NV12 semi-planar, NV12-QCOM, I420
  planar), and power-of-two downscaling so 4K sources can't blow up pool
  memory (staging capped at 1920×1080). Failed opens are sticky and safe.
- `frame_cache.h/.cpp` — a cached frame **keeps** its pool slot until
  eviction (the old code released the slot immediately after caching, then
  read it back later — dangling pointer / use-after-reuse). Evictions and
  `evictAll()` release slots back to the pool.
- `frame_pool.h/.cpp` — `mutable` mutex (the earlier `available() const`
  build error), and `configure()` is now grow-only so a second decoder can't
  wipe the arena while the cache holds slots in it.
- `gl_utils.h` — `EGL_OPENGL_ES3_BIT` fallback via `<EGL/eglext.h>`
  (`EGL_OPENGL_ES3_BIT_KHR`) for older NDK headers.

**Java:**

- `nativecore/NativeEngine.java` — `loadLibrary("c++_shared")` is now
  optional (try/catch). Works with both `c++_static` and `c++_shared`.
- `storage/MediaImporter.java` — picked images are recognized (`isImage`,
  `.jpg` fallback).
- `ui/editor/EditorActivity.java` — images now become `ImageClip`s on the
  video track instead of broken `AudioClip`s.

## One change YOU must make (I can't see your .mk files)

In `app/src/main/jni/Application.mk`, prefer the static STL:

```
APP_STL := c++_static
```

`libmontage_engine.so` then contains everything it needs — no dependency on
`libc++_shared.so` being packaged (a known AIDE weak point). If you keep
`c++_shared` and the app still dies with `UnsatisfiedLinkError`, that file
simply didn't make it into the APK.

## Still on your side (things I couldn't verify)

- `AndroidManifest.xml` must reference FQCNs:
  `com.videomontage.app.MontageApp`, `com.videomontage.ui.home.HomeActivity`,
  `com.videomontage.ui.editor.EditorActivity`.
- Image clips appear on the timeline and export fine as stills only once a
  Bitmap→GL path exists; today the native decoder opens video/audio only, so
  an image layer renders as the canvas color. The clip itself works
  (trim/move/split/undo).
- If anything still crashes at the **home** screen, that's resource-side
  (layout/drawable XML I can't see) — send the logcat.

## Round 3 — still crashing on "New Project" / opening a project (use-after-free)

Root cause, confirmed by reading every step from the button click down to the
JNI call: `jni_bridge.cpp`'s `unmarshal()` built each layer's `sourcePath` as
a raw `const char*` pointing into a `std::vector<std::string> pathStorage`
that was **local to `unmarshal()`**. The function returned `layers` but not
`pathStorage`, so `pathStorage` was destroyed the instant `unmarshal()`
returned — before `Engine::renderAt()` ever touched a single `sourcePath`.
Every layer's path was a dangling pointer by the time `Compositor::decoderFor()`
called `Decoder::open(layer.sourcePath, pool_)`, which constructs a
`std::string` by scanning that freed memory for a null terminator. On a
device running Android's Scudo hardened allocator (default since Android 11),
reading freed heap this way commonly walks into an unmapped page and SIGSEGVs
— an immediate, silent, whole-process kill with no Java stack trace, which
matches "crashes and instantly kills itself" exactly.

This fires on the **first rendered frame that contains any real clip** —
so it hits existing projects with media immediately, and a brand-new project
the moment a clip is imported (GLSurfaceView renders continuously, so this
happens within the first second either way).

Fix applied (two files, both in `jni/`):
- `compositor.h` — `LayerRequest::sourcePath` changed from `const char*` to
  `std::string`, so each layer owns a copy of its path instead of borrowing
  one that's about to be freed.
- `jni_bridge.cpp` — `unmarshal()` no longer builds a separate `pathStorage`
  vector; it assigns `l.sourcePath = utf;` directly (copying the bytes into
  the `LayerRequest` itself) while the JNI string chars are still valid, then
  releases them.

**This is a native-code fix — a Java-only re-run in AIDE will keep using the
old `.so`.** Do a full rebuild (NDK step included, not just recompile) before
testing, or you'll still see the old crash.

Not touched, but worth knowing about: `LayerMarshaller`'s buffers
(`paths`/`mvps`/`opacities`/`layerCount`) are shared, unsynchronized mutable
state read and written from two different threads — the GL thread's
continuous `renderFrame()` loop, and the UI thread's `renderSeek()` when the
user drags the scrubber. That's a data race independent of the bug above; it
just wasn't the cause of *this* crash since it needs a scrub gesture to fire.
Worth a synchronized/queued fix later if scrubbing during playback ever
produces its own crash.
