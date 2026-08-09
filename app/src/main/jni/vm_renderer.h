#pragma once
#include "gl_utils.h"
#include "frame.h"

namespace montage {

/** GPU stage of the pipeline. Owns the effect programs and a ping-pong
 *  FBO pair; each effect is one ping-pong pass, so N effects cost N passes
 *  and zero per-frame texture allocations. */
class Renderer {
public:
    bool init(int canvasWidth, int canvasHeight);
    void shutdown();

    /** Uploads a pool frame to a texture. Returns the texture id; the
     *  caller releases the pool slot afterwards. */
    GLuint upload(const Frame& frame);

    /** Runs the effect chain. `params` is a flat [kind, p0..p3] array,
     *  five floats per effect, handed down from the compositor. */
    GLuint applyEffects(GLuint source, const float* params, int effectCount);

    void drawLayer(GLuint texture, const float* mvp4x4, float opacity);
    void drawTransition(GLuint fromTex, GLuint toTex, float progress);

    int canvasWidth() const { return canvasW_; }
    int canvasHeight() const { return canvasH_; }

private:
    struct PingPong {
        GlFramebuffer a, b;
        bool frontIsA = true;
        void swap() { frontIsA = !frontIsA; }
        GlFramebuffer& target() { return frontIsA ? a : b; }
    };

    bool initPrograms();

    int canvasW_ = 0, canvasH_ = 0;
    GlProgram layer_;
    GlProgram colorGrade_;
    GlProgram blur_;
    GlProgram dissolve_;
    PingPong pingPong_;
    GLuint uploadTex_ = 0;
    int uploadW_ = 0, uploadH_ = 0;
};

} // namespace montage
