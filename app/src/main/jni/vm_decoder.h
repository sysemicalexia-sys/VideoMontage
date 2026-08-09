#pragma once
#include "frame.h"
#include "frame_pool.h"
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <string>
#include <atomic>

namespace montage {

/** One clip's video stream on MediaCodec. Seeking is keyframe-based:
 *  seekTo lands on the sync sample at-or-before the target, then
 *  decodeFrame drops frames until the exact PTS — frame-accurate without
 *  re-creating the codec.
 *
 *  ByteBuffer output is YUV 4:2:0 on virtually every device — it is
 *  converted to RGBA while copying into the pool, downscaled by a power of
 *  two when the source exceeds the staging cap (pool memory stays bounded
 *  for 4K sources). */
class Decoder {
public:
    Decoder() = default;
    ~Decoder();

    Decoder(const Decoder&) = delete;
    Decoder& operator=(const Decoder&) = delete;

    /** Failure is sticky: a failed Decoder stays failed (callers may store
     *  it instead of retrying every frame). */
    bool open(const std::string& uri, FramePool& pool);
    void close();

    /** Seeks so a following decodeFrame returns the frame at `ptsUs`. */
    bool seekTo(int64_t ptsUs);

    /** Decodes the frame at-or-after the current position into `out`.
     *  False at EOS. Frames with PTS < `untilUs` are dropped silently. */
    bool decodeFrame(Frame& out, int64_t untilUs);

    int64_t durationUs() const { return durationUs_; }
    int width() const { return outW_; }
    int height() const { return outH_; }
    float frameRate() const { return frameRate_; }
    bool hasAudio() const { return hasAudio_; }
    bool failed() const { return failed_; }

private:
    bool configureCodec();
    bool pumpInput();
    void readOutputFormat();
    void convertToRgba(const uint8_t* src, size_t size, uint8_t* dst) const;

    enum class Drain { Frame, Again, Eos };
    Drain drainStep(Frame& out, int64_t timeoutUs);

    AMediaExtractor* extractor_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    AMediaFormat* format_ = nullptr;
    FramePool* pool_ = nullptr;

    int videoTrack_ = -1;
    int width_ = 0, height_ = 0;      // coded size
    int outW_ = 0, outH_ = 0;         // staged size (after downscale)
    int downscale_ = 1;               // power-of-two decimation factor
    int colorFormat_ = 0;             // 0 = assume NV12 semi-planar
    int stride_ = 0;                  // 0 = tightly packed == width_
    int sliceHeight_ = 0;             // 0 = height_
    int64_t durationUs_ = 0;
    float frameRate_ = 30.f;
    bool hasAudio_ = false;
    bool inputEos_ = false;
    bool failed_ = false;
    std::atomic<bool> closed_{false};
};

} // namespace montage
