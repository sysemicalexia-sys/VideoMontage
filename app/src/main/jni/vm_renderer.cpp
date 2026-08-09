#include "vm_renderer.h"
#include "shaders.h"

namespace montage {

static const float kIdentity[16] = {
    1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1
};

static void drawFullscreen(GLuint program, const float* mvp) {
    glUniformMatrix4fv(uniform(program, "uMvp"), 1, GL_FALSE, mvp ? mvp : kIdentity);
    glDrawArrays(GL_TRIANGLES, 0, 3); // vertex stage derives the triangle from gl_VertexID
}

bool Renderer::init(int w, int h) {
    canvasW_ = w;
    canvasH_ = h;
    pingPong_.a.allocate(w, h);
    pingPong_.b.allocate(w, h);
    if (!initPrograms()) return false;
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    return true;
}

bool Renderer::initPrograms() {
    layer_      = compileProgram(shaders::kVertex, shaders::kLayer);
    colorGrade_ = compileProgram(shaders::kVertex, shaders::kColorGrade);
    blur_       = compileProgram(shaders::kVertex, shaders::kBlur);
    dissolve_   = compileProgram(shaders::kVertex, shaders::kDissolve);
    return layer_.id && colorGrade_.id && blur_.id && dissolve_.id;
}

GLuint Renderer::upload(const Frame& frame) {
    if (!uploadTex_ || uploadW_ != frame.width || uploadH_ != frame.height) {
        if (uploadTex_) glDeleteTextures(1, &uploadTex_);
        glGenTextures(1, &uploadTex_);
        glBindTexture(GL_TEXTURE_2D, uploadTex_);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        uploadW_ = frame.width;
        uploadH_ = frame.height;
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, frame.width, frame.height,
                     0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    }
    glBindTexture(GL_TEXTURE_2D, uploadTex_);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height,
                    GL_RGBA, GL_UNSIGNED_BYTE, frame.data);
    return uploadTex_;
}

GLuint Renderer::applyEffects(GLuint source, const float* params, int count) {
    if (count == 0) return source;
    glViewport(0, 0, canvasW_, canvasH_);
    GLuint current = source;
    for (int i = 0; i < count; ++i) {
        const float kind = params[i * 5];
        GLuint program = 0;

        pingPong_.target().bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, current);

        if (kind == 1.f) {        // color grade
            program = colorGrade_.id;
            glUseProgram(program);
            glUniform1f(uniform(program, "uExposure"),    params[i * 5 + 1]);
            glUniform1f(uniform(program, "uContrast"),    params[i * 5 + 2]);
            glUniform1f(uniform(program, "uSaturation"),  params[i * 5 + 3]);
            glUniform1f(uniform(program, "uTemperature"), params[i * 5 + 4]);
        } else if (kind == 2.f) { // blur: p0 radius, p1 axis (0=H, 1=V)
            program = blur_.id;
            glUseProgram(program);
            const bool horizontal = params[i * 5 + 2] < 0.5f;
            glUniform2f(uniform(program, "uDirection"),
                        horizontal ? 1.f / canvasW_ : 0.f,
                        horizontal ? 0.f : 1.f / canvasH_);
            glUniform1f(uniform(program, "uRadius"), params[i * 5 + 1]);
        } else {
            continue; // unknown node: skip rather than break the chain
        }
        glUniform1i(uniform(program, "uTex"), 0);
        drawFullscreen(program, nullptr);
        current = pingPong_.target().color.id;
        pingPong_.swap();
    }
    return current;
}

void Renderer::drawLayer(GLuint texture, const float* mvp, float opacity) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, canvasW_, canvasH_);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture);
    GLuint p = layer_.id;
    glUseProgram(p);
    glUniform1i(uniform(p, "uTex"), 0);
    glUniform1f(uniform(p, "uOpacity"), opacity);
    drawFullscreen(p, mvp);
}

void Renderer::drawTransition(GLuint fromTex, GLuint toTex, float progress) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, canvasW_, canvasH_);
    GLuint p = dissolve_.id;
    glUseProgram(p);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, fromTex);
    glUniform1i(uniform(p, "uFrom"), 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, toTex);
    glUniform1i(uniform(p, "uTo"), 1);
    glUniform1f(uniform(p, "uProgress"), progress);
    drawFullscreen(p, nullptr);
}

void Renderer::shutdown() {
    if (uploadTex_) { glDeleteTextures(1, &uploadTex_); uploadTex_ = 0; }
}

} // namespace montage
