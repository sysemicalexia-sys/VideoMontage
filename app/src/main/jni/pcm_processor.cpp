#include "pcm_processor.h"
#include <cmath>

namespace montage {

int16_t PcmProcessor::softClip(float v) {
    // tanh-shaped ceiling: keeps summed tracks from hard-clipping.
    float x = v / 32768.f;
    if (x > 1.5f) x = 1.5f; else if (x < -1.5f) x = -1.5f;
    float shaped = tanhf(x);
    return (int16_t)(shaped * 32767.f);
}

void PcmProcessor::applyGain(int16_t* pcm, size_t samples, float gain) {
    if (gain == 1.f) return;
    for (size_t i = 0; i < samples; ++i)
        pcm[i] = softClip(pcm[i] * gain);
}

void PcmProcessor::mixInto(int16_t* dst, const int16_t* src, size_t samples, float gain) {
    for (size_t i = 0; i < samples; ++i)
        dst[i] = softClip((float)dst[i] + src[i] * gain);
}

void PcmProcessor::crossfade(const int16_t* a, const int16_t* b, int16_t* out,
                             size_t samples, float t) {
    for (size_t i = 0; i < samples; ++i)
        out[i] = softClip(a[i] * (1.f - t) + b[i] * t);
}

void PcmProcessor::applyRamp(int16_t* pcm, size_t samples, float fromGain, float toGain) {
    if (samples == 0) return;
    for (size_t i = 0; i < samples; ++i) {
        float t = (float)i / (float)(samples - 1);
        pcm[i] = softClip(pcm[i] * (fromGain + (toGain - fromGain) * t));
    }
}

} // namespace montage
