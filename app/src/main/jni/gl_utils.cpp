#include "gl_utils.h"
#include <android/native_window.h>
#include <android/log.h>
#include <vector>

#define LOG_TAG "MontageGL"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace montage {

GlProgram::~GlProgram() { if (id) glDeleteProgram(id); }

GlProgram& GlProgram::operator=(GlProgram&& o) noexcept {
    if (this != &o) { if (id) glDeleteProgram(id); id = o.id; o.id = 0; }
    return *this;
}

void GlTexture::allocate(int w, int h) {
    width = w; height = h;
    if (!id) glGenTextures(1, &id);
    glBindTexture(GL_TEXTURE_2D, id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

GlTexture::~GlTexture() { if (id) glDeleteTextures(1, &id); }

void GlFramebuffer::allocate(int w, int h) {
    color.allocate(w, h);
    if (!id) glGenFramebuffers(1, &id);
    glBindFramebuffer(GL_FRAMEBUFFER, id);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.id, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) LOGE("FBO incomplete: 0x%x", status);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void GlFramebuffer::bind() const {
    glBindFramebuffer(GL_FRAMEBUFFER, id);
    glViewport(0, 0, color.width, color.height);
}

GlFramebuffer::~GlFramebuffer() { if (id) glDeleteFramebuffers(1, &id); }

static GLuint compileStage(GLenum stage, const char* src) {
    GLuint shader = glCreateShader(stage);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        GLint len = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &len);
        std::vector<char> log(len > 0 ? len : 1);
        glGetShaderInfoLog(shader, len, nullptr, log.data());
        LOGE("shader compile failed: %s", log.data());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GlProgram compileProgram(const char* vertexSrc, const char* fragmentSrc) {
    GlProgram program;
    GLuint vs = compileStage(GL_VERTEX_SHADER, vertexSrc);
    GLuint fs = compileStage(GL_FRAGMENT_SHADER, fragmentSrc);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return program; }
    program.id = glCreateProgram();
    glAttachShader(program.id, vs);
    glAttachShader(program.id, fs);
    glLinkProgram(program.id);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = GL_FALSE;
    glGetProgramiv(program.id, GL_LINK_STATUS, &ok);
    if (!ok) {
        LOGE("program link failed");
        glDeleteProgram(program.id);
        program.id = 0;
    }
    return program;
}

GLint uniform(GLuint program, const char* name) {
    return glGetUniformLocation(program, name);
}

bool EglContext::initDisplay(EGLConfig* out) {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY || !eglInitialize(display_, nullptr, nullptr)) return false;
    const EGLint attrs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT | EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLint count = 0;
    return eglChooseConfig(display_, attrs, out, 1, &count) && count > 0;
}

bool EglContext::createPBuffer(int width, int height, EGLContext shared) {
    EGLConfig config;
    if (!initDisplay(&config)) return false;
    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    context_ = eglCreateContext(display_, config, shared, ctxAttrs);
    if (context_ == EGL_NO_CONTEXT) return false;
    const EGLint surfAttrs[] = { EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE };
    surface_ = eglCreatePbufferSurface(display_, config, surfAttrs);
    return surface_ != EGL_NO_SURFACE;
}

bool EglContext::createWindowed(ANativeWindow* window, EGLContext shared) {
    EGLConfig config;
    if (!initDisplay(&config)) return false;
    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    context_ = eglCreateContext(display_, config, shared, ctxAttrs);
    if (context_ == EGL_NO_CONTEXT) return false;
    surface_ = eglCreateWindowSurface(display_, config, window, nullptr);
    return surface_ != EGL_NO_SURFACE;
}

bool EglContext::makeCurrent() {
    return eglMakeCurrent(display_, surface_, surface_, context_) == EGL_TRUE;
}

void EglContext::swap() { eglSwapBuffers(display_, surface_); }

void EglContext::destroy() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
        if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
        eglTerminate(display_);
    }
    display_ = EGL_NO_DISPLAY;
    context_ = EGL_NO_CONTEXT;
    surface_ = EGL_NO_SURFACE;
}

} // namespace montage
