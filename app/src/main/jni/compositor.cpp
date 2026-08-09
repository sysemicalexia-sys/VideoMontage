#include "compositor.h"

namespace montage {

bool Compositor::init(int w, int h) {
    canvasW_ = w;
    canvasH_ = h;
    // No GL here — init can run on threads with no current EGL context
    // (e.g. the export thread). The renderer is built lazily on first use.
    renderer_.reset();
    glContext_ = EGL_NO_CONTEXT;
    cache_.setPool(&pool_);
    return true;
}

bool Compositor::ensureRenderer() {
    EGLContext now = eglGetCurrentContext();
    if (now == EGL_NO_CONTEXT) return false; // no GL on this thread — never draw
    if (renderer_ && glContext_ == now) return true;
    // Context switched (preview ↔ export): objects from the old context are
    // invalid in the new one. The old renderer's GL deletes are inert without
    // its context bound, so this only leaks a handful of dead ids.
    renderer_.reset(new Renderer());
    if (!renderer_->init(canvasW_, canvasH_)) {
        renderer_.reset();
        return false;
    }
    glContext_ = now;
    return true;
}

Decoder* Compositor::decoderFor(const LayerRequest& layer) {
    // Decoders are index-addressed by clipKey; grow on demand. A failed open
    // is stored too — retrying every frame would leak an extractor per try.
    while ((int)decoders_.size() <= layer.clipKey) {
        decoders_.push_back(nullptr);
        decoderPtsUs_.push_back(-1);
    }
    if (!decoders_[layer.clipKey]) {
        auto d = std::unique_ptr<Decoder>(new Decoder());
        d->open(layer.sourcePath, pool_); // failure is sticky inside Decoder
        decoders_[layer.clipKey] = std::move(d);
    }
    return decoders_[layer.clipKey].get();
}

bool Compositor::renderFrame(const std::vector<LayerRequest>& layers, int64_t) {
    if (!ensureRenderer()) return false;
    glClearColor(0.039f, 0.043f, 0.055f, 1.f); // matches res ink color
    glClear(GL_COLOR_BUFFER_BIT);

    for (size_t li = 0; li < layers.size(); ++li) {
        const LayerRequest& layer = layers[li];
        Frame frame;
        const int64_t quantum = 33333; // 30 fps cache quantization
        // Keyed per clip AND per quantized PTS — clips share source times.
        const int64_t key = ((int64_t)layer.clipKey << 40)
                          | (layer.sourcePtsUs / quantum);

        GLuint tex = 0;
        if (cache_.get(key, generation_, frame)) {
            tex = renderer_->upload(frame); // slot stays owned by the cache
        } else {
            Decoder* dec = decoderFor(layer);
            if (!dec) continue;
            // The codec only moves forward. Scrubbing backward, or jumping
            // far ahead, needs a keyframe seek; steady playback just decodes on.
            int64_t& lastPts = decoderPtsUs_[layer.clipKey];
            if (lastPts < 0 || layer.sourcePtsUs < lastPts
                    || layer.sourcePtsUs - lastPts > 2000000) {
                dec->seekTo(layer.sourcePtsUs);
            }
            if (dec->decodeFrame(frame, layer.sourcePtsUs)) {
                lastPts = frame.ptsUs;
                tex = renderer_->upload(frame);
                cache_.put(key, frame, generation_); // cache owns the slot now
            }
        }
        if (!tex) continue;

        GLuint finalTex = layer.effectParams.empty()
            ? tex
            : renderer_->applyEffects(tex, layer.effectParams.data(),
                                      (int)layer.effectParams.size() / 5);
        renderer_->drawLayer(finalTex, layer.mvp, layer.opacity);
    }
    return true;
}

void Compositor::shutdown() {
    decoders_.clear();
    decoderPtsUs_.clear();
    cache_.evictAll();
    renderer_.reset();
    glContext_ = EGL_NO_CONTEXT;
}

} // namespace montage
