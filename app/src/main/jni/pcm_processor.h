#pragma once
#include <cstdint>
#include <cstddef>

namespace montage {

/** Interleaved stereo PCM at 44.1 kHz — the canonical format every audio
 *  path speaks. Resampling to 44.1 happens at the decoder boundary so the
 *  mixer never deals with rate conversion mid-graph. */
class PcmProcessor {
public:
    /** g in linear gain; applied in place with soft clipping. */
    static void applyGain(int16_t* pcm, size_t samples, float gain);

    /** Linear crossfade between two equally-sized buffers into `out`. */
    static void crossfade(const int16_t* a, const int16_t* b, int16_t* out,
                          size_t samples, float t);

    /** Sums `src` into `dst` with gain; soft-clips the mix. */
    static void mixInto(int16_t* dst, const int16_t* src, size_t samples, float gain);

    /** Fade-in/out ramp across a buffer. */
    static void applyRamp(int16_t* pcm, size_t samples, float fromGain, float toGain);

private:
    static int16_t softClip(float v);
};

} // namespace montage
