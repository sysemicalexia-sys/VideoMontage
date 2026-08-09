#pragma once
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

/* Older NDK EGL headers only expose the ES3 renderable bit as the KHR
 * variant from eglext.h (EGL 1.5 renamed it without the suffix). */
#ifndef EGL_OPENGL_ES3_BIT
#  ifdef EGL_OPENGL_ES3_BIT_KHR
#    define EGL_OPENGL_ES3_BIT EGL_OPENGL_ES3_BIT_KHR
#  else
#    define EGL_OPENGL_ES3_BIT 0x00000040
#  endif
#endif

struct ANativeWindow;

namespace montage {

struct GlProgram {
    GLuint id = 0;
    ~GlProgram();
    GlProgram() = default;
    GlProgram(const GlProgram&) = delete;
    GlProgram& operator=(const GlProgram&) = delete;
    GlProgram(GlProgram&& o) noexcept : id(o.id) { o.id = 0; }
    GlProgram& operator=(GlProgram&& o) noexcept;
};

struct GlTexture {
    GLuint id = 0;
    int width = 0, height = 0;
    void allocate(int w, int h);
    ~GlTexture();
    GlTexture() = default;
    GlTexture(const GlTexture&) = delete;
    GlTexture& operator=(const GlTexture&) = delete;
};

struct GlFramebuffer {
    GLuint id = 0;
    GlTexture color;
    void allocate(int w, int h);
    void bind() const;
    ~GlFramebuffer();
    GlFramebuffer() = default;
    GlFramebuffer(const GlFramebuffer&) = delete;
    GlFramebuffer& operator=(const GlFramebuffer&) = delete;
};

GlProgram compileProgram(const char* vertexSrc, const char* fragmentSrc);
GLint uniform(GLuint program, const char* name);

/** RAII EGL context for offscreen render/encode threads. */
class EglContext {
public:
    bool createPBuffer(int width, int height, EGLContext shared = EGL_NO_CONTEXT);
    bool createWindowed(ANativeWindow* window, EGLContext shared = EGL_NO_CONTEXT);
    bool makeCurrent();
    void swap();
    void destroy();
    EGLContext handle() const { return context_; }
    ~EglContext() { destroy(); }
private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    bool initDisplay(EGLConfig* out);
};

} // namespace montage
