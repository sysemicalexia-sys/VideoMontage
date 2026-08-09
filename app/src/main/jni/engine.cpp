#include "engine.h"
#include <android/log.h>

#define LOG_TAG "MontageEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace montage {

Engine& Engine::instance() {
    static Engine e;
    return e;
}

bool Engine::init(int canvasW, int canvasH) {
    std::lock_guard<std::mutex> lock(glMutex_);
    if (initialized_) return true;
    initialized_ = compositor_.init(canvasW, canvasH);
    return initialized_;
}

bool Engine::attachPreview(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(glMutex_);
    // The GLSurfaceView GL thread calls this with ITS context current — that
    // context is the preview GL context. We only track the window size;
    // actual drawing happens in renderAt on the same thread, and
    // GLSurfaceView swaps buffers itself after onDrawFrame returns.
    (void)window;
    return initialized_;
}

void Engine::detachPreview() {
    // Nothing to release: the preview EGL context belongs to GLSurfaceView.
}

bool Engine::renderAt(const std::vector<LayerRequest>& layers, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(glMutex_);
    if (!initialized_ || exporting_) return false;
    return compositor_.renderFrame(layers, ptsUs); // no swap — GLSurfaceView owns presentation
}

bool Engine::startExport(const char* outputPath, int width, int height, int frameRate) {
    std::lock_guard<std::mutex> lock(glMutex_);
    if (exporting_) return false;
    encoder_ = std::unique_ptr<Encoder>(new Encoder());
    Encoder::Config cfg;
    cfg.width = width;
    cfg.height = height;
    cfg.frameRate = frameRate;
    if (!encoder_->start(outputPath, cfg)) {
        encoder_.reset();
        return false;
    }
    if (!exportEgl_.createWindowed(encoder_->inputSurface()) || !exportEgl_.makeCurrent()) {
        LOGE("export EGL failed");
        encoder_.reset();
        return false;
    }
    exporting_ = true;
    return true;
}

bool Engine::exportFrame(const std::vector<LayerRequest>& layers, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(glMutex_);
    if (!exporting_ || !encoder_) return false;
    if (!compositor_.renderFrame(layers, ptsUs)) return false;
    exportEgl_.swap();
    return encoder_->submitVideoFrame(ptsUs);
}

bool Engine::finishExport() {
    exporting_ = false;
    bool ok = encoder_ && encoder_->finish();
    encoder_.reset();
    exportEgl_.destroy();
    return ok;
}

void Engine::cancelExport() {
    if (encoder_) encoder_->abort();
    finishExport();
}

float Engine::exportProgress() const {
    return encoder_ ? encoder_->progress() : 0.f;
}

void Engine::invalidateTimeline() {
    compositor_.invalidate();
}

void Engine::shutdown() {
    std::lock_guard<std::mutex> lock(glMutex_);
    exporting_ = false;
    encoder_.reset();
    compositor_.shutdown();
    exportEgl_.destroy();
    initialized_ = false;
}

} // namespace montage
