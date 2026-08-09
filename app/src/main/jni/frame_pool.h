#pragma once
#include "frame.h"
#include <mutex>
#include <vector>

namespace montage {

/** Fixed-size arena of frame buffers. Decoders borrow, renderers return —
 *  zero allocations on the per-frame hot path. Sized from the largest
 *  source resolution at open time. */
class FramePool {
public:
    FramePool() = default;
    ~FramePool() = default;

    FramePool(const FramePool&) = delete;
    FramePool& operator=(const FramePool&) = delete;

    void configure(int width, int height, int slots = 8);

    /** Returns a slot index or -1 if the pool is exhausted (caller must
     *  drop the frame rather than allocate on the hot path). */
    int32_t acquire();
    void release(int32_t slot);

    uint8_t* bufferOf(int32_t slot);
    size_t strideBytes() const { return strideBytes_; }
    int available() const;

private:
    mutable std::mutex mutex_;
    std::vector<uint8_t> arena_;
    std::vector<bool> inUse_;
    size_t strideBytes_ = 0;
    int width_ = 0, height_ = 0;
};

} // namespace montage
