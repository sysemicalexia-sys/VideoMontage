#pragma once
#include "compositor.h"
#include "vm_encoder.h"
#include "gl_utils.h"
#include <android/native_window.h>
#include <atomic>
#include <memory>
#include <mutex>

namespace montage {

/** Root of the native pipeline. One Engine per process; Java drives
 *  everything through it. Business logic lives here — JNI only marshals. */
class Engine {
public:
    static Engine& instance();

    bool init(int canvasW, int canvasH);

    /** Preview surface lifecycle, driven from GLSurfaceView callbacks. */
    bool attachPreview(ANativeWindow* window);
    void detachPreview();

    /** Composites one instant onto the preview surface. */
    bool renderAt(const std::vector<LayerRequest>& layers, int64_t timelinePtsUs);

    /** Export: renders the timeline range straight into the encoder. */
    bool startExport(const char* outputPath, int width, int height, int frameRate);
    bool exportFrame(const std::vector<LayerRequest>& layers, int64_t ptsUs);
    bool finishExport();
    void cancelExport();
    float exportProgress() const;

    void invalidateTimeline();
    void shutdown();

private:
    Engine() = default;

    std::mutex glMutex_;          // preview vs export surface arbitration
    // NOTE: no EglContext for preview. GLSurfaceView already owns an EGL
    // context + window surface on its GL thread — creating a second EGL
    // surface on the same ANativeWindow is EGL_BAD_ALLOC per spec and
    // crashes real drivers. Preview renders into the current context.
    EglContext exportEgl_;
    Compositor compositor_;
    std::unique_ptr<Encoder> encoder_;
    bool initialized_ = false;
    std::atomic<bool> exporting_{false};
};

} // namespace montage
