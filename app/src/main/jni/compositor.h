#pragma once
#include "vm_decoder.h"
#include "vm_renderer.h"
#include "frame_cache.h"
#include "frame_pool.h"
#include <EGL/egl.h>
#include <memory>
#include <string>
#include <vector>

namespace montage {

/** Flat, render-ready view of the timeline at one instant. Java builds the
 *  layer list per frame from the immutable timeline; the compositor turns
 *  it into GL draw calls. Data crosses JNI as plain arrays, not objects —
 *  the JNI boundary stays dumb on purpose. */
struct LayerRequest {
    int clipKey;              // stable id → decoder slot
    std::string sourcePath;   // opened lazily on first sight; owns its bytes
    int64_t sourcePtsUs;      // already speed/trim mapped on the Java side
    float mvp[16];
    float opacity;
    std::vector<float> effectParams; // [kind, p0..p3] * n
};

class Compositor {
public:
    /** Records canvas size only — deliberately no GL work. GL objects are
     *  created lazily on the first render, under whatever EGL context is
     *  current on the calling thread (GLSurfaceView's for preview, the
     *  encoder's for export). Safe to call from any thread. */
    bool init(int canvasW, int canvasH);
    void shutdown();

    /** Composites one timeline instant onto the current framebuffer.
     *  Requires a current EGL context on the calling thread. */
    bool renderFrame(const std::vector<LayerRequest>& layers, int64_t timelinePtsUs);

    /** Bumps the cache generation; call on any timeline mutation. */
    void invalidate() { ++generation_; cache_.evictAll(); }

    Renderer& renderer() { return *renderer_; }

private:
    Decoder* decoderFor(const LayerRequest& layer);
    bool ensureRenderer();

    std::unique_ptr<Renderer> renderer_;  // per-EGL-context; rebuilt on switch
    EGLContext glContext_ = EGL_NO_CONTEXT;
    int canvasW_ = 0, canvasH_ = 0;

    FramePool pool_;
    FrameCache cache_{6};       // ≤ pool slots − 2: cached frames hold slots
    uint32_t generation_ = 1;
    std::vector<std::unique_ptr<Decoder>> decoders_;
    std::vector<int64_t> decoderPtsUs_;   // per-decoder stream position (seek logic)
};

} // namespace montage
