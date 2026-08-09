#include "frame_pool.h"

namespace montage {

void FramePool::configure(int width, int height, int slots) {
    std::lock_guard<std::mutex> lock(mutex_);
    // Grow-only: decoders share one pool, and cached frames hold slots —
    // re-initializing the arena under them would orphan every cached frame.
    const size_t need = static_cast<size_t>(width) * height * 4; // RGBA staging
    if (!arena_.empty() && need <= strideBytes_
            && static_cast<int>(inUse_.size()) >= slots) return;
    width_ = width;
    height_ = height;
    strideBytes_ = need;
    arena_.assign(strideBytes_ * slots, 0);
    inUse_.assign(slots, false);
}

int32_t FramePool::acquire() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (size_t i = 0; i < inUse_.size(); ++i) {
        if (!inUse_[i]) {
            inUse_[i] = true;
            return static_cast<int32_t>(i);
        }
    }
    return -1;
}

void FramePool::release(int32_t slot) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (slot >= 0 && static_cast<size_t>(slot) < inUse_.size()) inUse_[slot] = false;
}

uint8_t* FramePool::bufferOf(int32_t slot) {
    if (slot < 0 || static_cast<size_t>(slot) >= inUse_.size()) return nullptr;
    return arena_.data() + strideBytes_ * slot;
}

int FramePool::available() const {
    std::lock_guard<std::mutex> lock(mutex_);
    int n = 0;
    for (bool u : inUse_) if (!u) ++n;
    return n;
}

} // namespace montage
