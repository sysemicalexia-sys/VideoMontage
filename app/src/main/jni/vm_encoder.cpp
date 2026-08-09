#include "vm_encoder.h"
#include <android/log.h>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

#define LOG_TAG "MontageEncoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace montage {

static const int COLOR_FORMAT_SURFACE = 2130708361;
static const int AAC_PROFILE_LC = 2;

bool Encoder::start(const std::string& outputPath, const Config& config) {
    config_ = config;

    AMediaFormat* vfmt = AMediaFormat_new();
    AMediaFormat_setString(vfmt, AMEDIAFORMAT_KEY_MIME, "video/avc");
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_WIDTH, config.width);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_HEIGHT, config.height);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_BIT_RATE, config.videoBitrate);
    AMediaFormat_setFloat(vfmt, AMEDIAFORMAT_KEY_FRAME_RATE, (float)config.frameRate);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_COLOR_FORMAT, COLOR_FORMAT_SURFACE);

    videoCodec_ = AMediaCodec_createEncoderByType("video/avc");
    if (!videoCodec_) return false;
    if (AMediaCodec_configure(videoCodec_, vfmt, nullptr, nullptr,
                              AMEDIACODEC_CONFIGURE_FLAG_ENCODE) != AMEDIA_OK)
        return false;
    if (AMediaCodec_createInputSurface(videoCodec_, &inputSurface_) != AMEDIA_OK)
        return false;
    AMediaCodec_start(videoCodec_);
    AMediaFormat_delete(vfmt);

    AMediaFormat* afmt = AMediaFormat_new();
    AMediaFormat_setString(afmt, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, config.audioSampleRate);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, config.audioChannels);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_BIT_RATE, config.audioBitrate);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_AAC_PROFILE, AAC_PROFILE_LC);
    audioCodec_ = AMediaCodec_createEncoderByType("audio/mp4a-latm");
    if (!audioCodec_) return false;
    AMediaCodec_configure(audioCodec_, afmt, nullptr, nullptr,
                          AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaCodec_start(audioCodec_);
    AMediaFormat_delete(afmt);

    // The NDK muxer takes an fd, not a path — we own opening and closing.
    muxerFd_ = open(outputPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (muxerFd_ < 0) return false;
    muxer_ = AMediaMuxer_new(muxerFd_, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    if (!muxer_) {
        close(muxerFd_);
        muxerFd_ = -1;
        return false;
    }
    return true;
}

void Encoder::drainVideo(bool endOfStream) {
    if (endOfStream) AMediaCodec_signalEndOfInputStream(videoCodec_);
    AMediaCodecBufferInfo info;
    while (true) {
        ssize_t idx = AMediaCodec_dequeueOutputBuffer(videoCodec_, &info, endOfStream ? -1 : 0);
        if (idx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) break;
        if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* fmt = AMediaCodec_getOutputFormat(videoCodec_);
            videoTrackIdx_ = AMediaMuxer_addTrack(muxer_, fmt);
            if (!muxerStarted_ && audioTrackIdx_ >= 0) {
                AMediaMuxer_start(muxer_);
                muxerStarted_ = true;
            }
            continue;
        }
        if (idx < 0) continue;
        size_t size = 0;
        uint8_t* buf = AMediaCodec_getOutputBuffer(videoCodec_, idx, &size);
        if (muxerStarted_ && info.size > 0) {
            AMediaMuxer_writeSampleData(muxer_, videoTrackIdx_, buf, &info);
            ++encodedFrames_;
            if (totalFrames_ > 0)
                progress_ = (float)encodedFrames_ / (float)totalFrames_;
        }
        AMediaCodec_releaseOutputBuffer(videoCodec_, idx, false);
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) break;
    }
}

void Encoder::drainAudio(bool endOfStream) {
    AMediaCodecBufferInfo info;
    while (true) {
        ssize_t idx = AMediaCodec_dequeueOutputBuffer(audioCodec_, &info, 0);
        if (idx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) break;
        if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* fmt = AMediaCodec_getOutputFormat(audioCodec_);
            audioTrackIdx_ = AMediaMuxer_addTrack(muxer_, fmt);
            if (!muxerStarted_ && videoTrackIdx_ >= 0) {
                AMediaMuxer_start(muxer_);
                muxerStarted_ = true;
            }
            continue;
        }
        if (idx < 0) continue;
        size_t size = 0;
        uint8_t* buf = AMediaCodec_getOutputBuffer(audioCodec_, idx, &size);
        if (muxerStarted_ && info.size > 0)
            AMediaMuxer_writeSampleData(muxer_, audioTrackIdx_, buf, &info);
        AMediaCodec_releaseOutputBuffer(audioCodec_, idx, false);
    }
}

bool Encoder::writeAudioFrame(const int16_t* pcm, size_t samples, int64_t ptsUs) {
    if (aborted_) return false;
    ssize_t idx = AMediaCodec_dequeueInputBuffer(audioCodec_, 50000);
    if (idx < 0) return true; // encoder busy; caller retries
    size_t cap = 0;
    uint8_t* buf = AMediaCodec_getInputBuffer(audioCodec_, idx, &cap);
    size_t bytes = samples * sizeof(int16_t);
    if (bytes > cap) bytes = cap;
    memcpy(buf, pcm, bytes);
    AMediaCodec_queueInputBuffer(audioCodec_, idx, 0, bytes, ptsUs, 0);
    drainAudio(false);
    return true;
}

bool Encoder::submitVideoFrame(int64_t ptsUs) {
    if (aborted_) return false;
    drainVideo(false);
    ++totalFrames_;
    return true;
}

bool Encoder::finish() {
    drainVideo(true);
    drainAudio(true);
    if (muxer_) { if (muxerStarted_) AMediaMuxer_stop(muxer_); AMediaMuxer_delete(muxer_); }
    if (muxerFd_ >= 0) { close(muxerFd_); muxerFd_ = -1; }
    if (videoCodec_) { AMediaCodec_stop(videoCodec_); AMediaCodec_delete(videoCodec_); }
    if (audioCodec_) { AMediaCodec_stop(audioCodec_); AMediaCodec_delete(audioCodec_); }
    if (inputSurface_) ANativeWindow_release(inputSurface_);
    muxer_ = nullptr; videoCodec_ = nullptr; audioCodec_ = nullptr; inputSurface_ = nullptr;
    return !aborted_;
}

void Encoder::abort() { aborted_ = true; }

} // namespace montage
