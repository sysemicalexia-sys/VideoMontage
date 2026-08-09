#pragma once
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaMuxer.h>
#include <android/native_window.h>
#include <string>
#include <atomic>

namespace montage {

/** H.264/AAC encode + MP4 mux. Video frames arrive as GL textures on an
 *  encoder-input surface — the compositor renders straight into the codec
 *  so export never reads pixels back to the CPU. */
class Encoder {
public:
    struct Config {
        int width = 1920;
        int height = 1080;
        int frameRate = 30;
        int videoBitrate = 12000000;
        int audioSampleRate = 44100;
        int audioChannels = 2;
        int audioBitrate = 192000;
    };

    bool start(const std::string& outputPath, const Config& config);
    bool writeAudioFrame(const int16_t* pcm, size_t samples, int64_t ptsUs);
    bool submitVideoFrame(int64_t ptsUs); // called after GL render into input surface
    bool finish();
    void abort();

    ANativeWindow* inputSurface() const { return inputSurface_; }
    float progress() const { return progress_.load(); }

private:
    void drainVideo(bool endOfStream);
    void drainAudio(bool endOfStream);

    AMediaCodec* videoCodec_ = nullptr;
    AMediaCodec* audioCodec_ = nullptr;
    AMediaMuxer* muxer_ = nullptr;
    ANativeWindow* inputSurface_ = nullptr;
    int muxerFd_ = -1;

    int videoTrackIdx_ = -1;
    int audioTrackIdx_ = -1;
    bool muxerStarted_ = false;
    int64_t totalFrames_ = 0;
    int64_t encodedFrames_ = 0;
    Config config_;
    std::atomic<float> progress_{0.f};
    std::atomic<bool> aborted_{false};
};

} // namespace montage
