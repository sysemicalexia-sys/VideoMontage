#include "vm_decoder.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "MontageDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace montage {

// Android color formats (OMX): only the 4:2:0 family shows up in ByteBuffer mode.
static const int kFormatYUV420Planar        = 19;         // I420: Y U V planes
static const int kFormatYUV420SemiPlanar    = 21;         // NV12: Y + interleaved UV
static const int kFormatYUV420PackedSemi    = 39;         // NV12 variant
static const int kFormatQcomYUV420Semi      = 2141391872; // 0x7FA30C00, NV12 layout

// Staging cap: bounds pool memory for 4K+ sources (1080p RGBA ≈ 8.3 MB/slot).
static const int kMaxStageW = 1920;
static const int kMaxStageH = 1080;

Decoder::~Decoder() { close() ; }

bool Decoder::open(const std::string& uri, FramePool& pool) {
    pool_ = &pool;
    extractor_ = AMediaExtractor_new();
    if (!extractor_ || AMediaExtractor_setDataSource(extractor_, uri.c_str()) != AMEDIA_OK) {
        LOGE("cannot open %s", uri.c_str());
        close();
        failed_ = true;
        return false;
    }

    const size_t trackCount = AMediaExtractor_getTrackCount(extractor_);
    for (size_t i = 0; i < trackCount; ++i) {
        AMediaFormat* fmt = AMediaExtractor_getTrackFormat(extractor_, i);
        const char* mime = nullptr;
        AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
        if (mime && strncmp(mime, "video/", 6) == 0 && videoTrack_ < 0) {
            videoTrack_ = static_cast<int>(i);
            format_ = fmt;
        } else {
            if (mime && strncmp(mime, "audio/", 6) == 0) hasAudio_ = true;
            AMediaFormat_delete(fmt);
        }
    }
    if (videoTrack_ < 0) {
        close();
        failed_ = true;
        return false;
    }

    AMediaExtractor_selectTrack(extractor_, videoTrack_);
    AMediaFormat_getInt32(format_, AMEDIAFORMAT_KEY_WIDTH, &width_);
    AMediaFormat_getInt32(format_, AMEDIAFORMAT_KEY_HEIGHT, &height_);
    AMediaFormat_getInt64(format_, AMEDIAFORMAT_KEY_DURATION, &durationUs_);
    AMediaFormat_getFloat(format_, AMEDIAFORMAT_KEY_FRAME_RATE, &frameRate_);

    // Power-of-two decimation until the staged frame fits the cap.
    downscale_ = 1;
    while (downscale_ < 4
            && (width_ / downscale_) * (height_ / downscale_) > kMaxStageW * kMaxStageH)
        downscale_ *= 2;
    outW_ = width_ / downscale_;
    outH_ = height_ / downscale_;
    pool_->configure(outW_, outH_);

    if (!configureCodec()) {
        close();
        failed_ = true;
        return false;
    }
    return true;
}

bool Decoder::configureCodec() {
    const char* mime = nullptr;
    AMediaFormat_getString(format_, AMEDIAFORMAT_KEY_MIME, &mime);
    codec_ = AMediaCodec_createDecoderByType(mime);
    if (!codec_) return false;
    // ByteBuffer output: frames are YUV-converted into the pool, then uploaded to GL.
    return AMediaCodec_configure(codec_, format_, nullptr, nullptr, 0) == AMEDIA_OK
        && AMediaCodec_start(codec_) == AMEDIA_OK;
}

void Decoder::readOutputFormat() {
    AMediaFormat* fmt = AMediaCodec_getOutputFormat(codec_);
    if (!fmt) return;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_COLOR_FORMAT, &colorFormat_);
    AMediaFormat_getInt32(fmt, "stride", &stride_);
    AMediaFormat_getInt32(fmt, "slice-height", &sliceHeight_);
    AMediaFormat_delete(fmt);
}

bool Decoder::seekTo(int64_t ptsUs) {
    if (!extractor_ || closed_ || failed_) return false;
    AMediaCodec_flush(codec_);
    inputEos_ = false;
    return AMediaExtractor_seekTo(extractor_, ptsUs, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC)
           == AMEDIA_OK;
}

bool Decoder::pumpInput() {
    if (inputEos_) return false;
    ssize_t bufIdx = AMediaCodec_dequeueInputBuffer(codec_, 0);
    if (bufIdx < 0) return false; // codec busy; output drain will free one
    size_t bufSize = 0;
    uint8_t* buf = AMediaCodec_getInputBuffer(codec_, bufIdx, &bufSize);
    ssize_t sampleSize = AMediaExtractor_readSampleData(extractor_, buf, bufSize);
    if (sampleSize < 0) {
        AMediaCodec_queueInputBuffer(codec_, bufIdx, 0, 0, 0,
                                     AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
        inputEos_ = true;
        return false;
    }
    int64_t pts = AMediaExtractor_getSampleTime(extractor_);
    AMediaCodec_queueInputBuffer(codec_, bufIdx, 0,
                                 static_cast<size_t>(sampleSize), pts, 0);
    AMediaExtractor_advance(extractor_);
    return true;
}

static inline uint8_t clamp8(int v) {
    return static_cast<uint8_t>(v < 0 ? 0 : v > 255 ? 255 : v);
}

/** YUV 4:2:0 → RGBA with power-of-two decimation. Handles NV12 semi-planar
 *  (the default on Android) and I420 planar; BT.601 video-range, which is
 *  what MediaCodec outputs. */
void Decoder::convertToRgba(const uint8_t* src, size_t size, uint8_t* dst) const {
    const int yStride = stride_ > 0 ? stride_ : width_;
    const int sliceH = sliceHeight_ > 0 ? sliceHeight_ : height_;
    const uint8_t* yPlane = src;
    const uint8_t* uvPlane = src + static_cast<size_t>(yStride) * sliceH;

    const bool planar = colorFormat_ == kFormatYUV420Planar;
    const int uvStride = planar ? yStride / 2 : yStride;
    const uint8_t* uPlane = uvPlane;
    const uint8_t* vPlane = planar
            ? uvPlane + static_cast<size_t>(uvStride) * (sliceH / 2)
            : uvPlane + 1;
    const int uvStep = planar ? 1 : 2;

    const int ds = downscale_;
    for (int oy = 0; oy < outH_; ++oy) {
        const int sy = oy * ds;
        const uint8_t* yRow = yPlane + static_cast<size_t>(sy) * yStride;
        const uint8_t* uRow = uPlane + static_cast<size_t>(sy / 2) * uvStride;
        const uint8_t* vRow = vPlane + static_cast<size_t>(sy / 2) * uvStride;
        uint8_t* d = dst + static_cast<size_t>(oy) * outW_ * 4;
        for (int ox = 0; ox < outW_; ++ox) {
            const int sx = ox * ds;
            const int c = yRow[sx] - 16 < 0 ? 0 : yRow[sx] - 16;
            const int u = uRow[(sx / 2) * uvStep] - 128;
            const int v = vRow[(sx / 2) * uvStep] - 128;
            d[0] = clamp8((298 * c + 409 * v + 128) >> 8);
            d[1] = clamp8((298 * c - 100 * u - 208 * v + 128) >> 8);
            d[2] = clamp8((298 * c + 516 * u + 128) >> 8);
            d[3] = 0xFF;
            d += 4;
        }
    }
}

Decoder::Drain Decoder::drainStep(Frame& out, int64_t timeoutUs) {
    AMediaCodecBufferInfo info;
    ssize_t idx = AMediaCodec_dequeueOutputBuffer(codec_, &info, timeoutUs);
    if (idx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) return Drain::Again;
    if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        readOutputFormat();
        return Drain::Again;
    }
    if (idx < 0) return Drain::Eos;
    if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
        AMediaCodec_releaseOutputBuffer(codec_, idx, false);
        return Drain::Eos;
    }

    int32_t slot = pool_->acquire();
    if (slot < 0) {
        // Pool exhausted: renderer is behind. Drop rather than allocate —
        // scrub smoothness beats completeness mid-drag.
        AMediaCodec_releaseOutputBuffer(codec_, idx, false);
        return Drain::Again;
    }
    size_t size = 0;
    uint8_t* buf = AMediaCodec_getOutputBuffer(codec_, idx, &size);
    if (buf && info.size > 0) {
        convertToRgba(buf, static_cast<size_t>(info.size), pool_->bufferOf(slot));
    }
    AMediaCodec_releaseOutputBuffer(codec_, idx, false);

    out.data = pool_->bufferOf(slot);
    out.poolSlot = slot;
    out.width = outW_;
    out.height = outH_;
    out.ptsUs = info.presentationTimeUs;
    out.format = PixelFormat::RGBA_8888;
    return Drain::Frame;
}

bool Decoder::decodeFrame(Frame& out, int64_t untilUs) {
    if (closed_ || failed_ || !codec_) return false;
    while (!closed_) {
        pumpInput();
        switch (drainStep(out, 10000)) {
            case Drain::Frame:
                if (out.ptsUs < untilUs) {
                    pool_->release(out.poolSlot);
                    out.reset();
                    break; // catch-up drop after a seek
                }
                return true;
            case Drain::Eos:
                return false;
            case Drain::Again:
                if (inputEos_) return false;
                break;
        }
    }
    return false;
}

void Decoder::close() {
    closed_ = true;
    if (codec_) { AMediaCodec_stop(codec_); AMediaCodec_delete(codec_); codec_ = nullptr; }
    if (extractor_) { AMediaExtractor_delete(extractor_); extractor_ = nullptr; }
    if (format_) { AMediaFormat_delete(format_); format_ = nullptr; }
}

} // namespace montage
