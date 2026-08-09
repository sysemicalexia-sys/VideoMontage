#pragma once
#include <cstdint>
#include <cstddef>

namespace montage {

enum class PixelFormat : uint8_t { RGBA_8888, NV12, OES_TEXTURE };

/** A single decoded video frame. Buffers come from FramePool — a Frame
 *  never owns heap memory directly, it owns a pool slot. */
struct Frame {
    uint8_t* data = nullptr;      // CPU-accessible pixels (staging)
    uint32_t oesTexture = 0;      // when decoded straight to a GL texture
    int width = 0;
    int height = 0;
    int64_t ptsUs = 0;            // presentation time, source clock
    int32_t poolSlot = -1;
    PixelFormat format = PixelFormat::RGBA_8888;
    uint32_t generation = 0;      // invalidated when the clip list changes

    bool valid() const { return data != nullptr || oesTexture != 0; }
    void reset() { data = nullptr; oesTexture = 0; width = height = 0; ptsUs = 0; poolSlot = -1; }
};

} // namespace montage
