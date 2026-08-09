#include "engine.h"
#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include <android/native_window_jni.h>

using namespace montage;

/** JNI is a marshaling layer only — every decision happens in Engine.
 *  Layer wire format (parallel arrays, see NativeEngine.java):
 *    paths[i]       source file for layer i
 *    clipKeys[i]    stable clip index (decoder slot)
 *    sourcePts[i]   source PTS in µs, speed/trim already applied
 *    mvps[i*16..]   column-major transform
 *    opacities[i]
 *    effects        flat [kind,p0,p1,p2,p3] runs; effectCounts[i] = run length
 */
static std::vector<LayerRequest> unmarshal(
        JNIEnv* env, jobjectArray paths, jintArray clipKeys, jlongArray sourcePts,
        jfloatArray mvps, jfloatArray opacities, jfloatArray effects, jintArray effectCounts) {

    const jsize n = env->GetArrayLength(paths);
    jint* keys = env->GetIntArrayElements(clipKeys, nullptr);
    jlong* pts = env->GetLongArrayElements(sourcePts, nullptr);
    jfloat* mvp = env->GetFloatArrayElements(mvps, nullptr);
    jfloat* opa = env->GetFloatArrayElements(opacities, nullptr);
    jfloat* fx = effects ? env->GetFloatArrayElements(effects, nullptr) : nullptr;
    jint* fxCounts = effectCounts ? env->GetIntArrayElements(effectCounts, nullptr) : nullptr;

    std::vector<LayerRequest> layers(n);
    const jfloat* fxCursor = fx;
    for (jsize i = 0; i < n; ++i) {
        jstring jpath = (jstring)env->GetObjectArrayElement(paths, i);
        const char* utf = env->GetStringUTFChars(jpath, nullptr);

        LayerRequest& l = layers[i];
        l.clipKey = keys[i];
        l.sourcePath = utf;   // std::string copies the bytes now, while utf is still valid
        l.sourcePtsUs = pts[i];

        env->ReleaseStringUTFChars(jpath, utf);
        env->DeleteLocalRef(jpath);
        memcpy(l.mvp, mvp + i * 16, 16 * sizeof(float));
        l.opacity = opa[i];
        if (fx && fxCounts) {
            const int count = fxCounts[i];
            l.effectParams.assign(fxCursor, fxCursor + count * 5);
            fxCursor += count * 5;
        }
    }

    env->ReleaseIntArrayElements(clipKeys, keys, JNI_ABORT);
    env->ReleaseLongArrayElements(sourcePts, pts, JNI_ABORT);
    env->ReleaseFloatArrayElements(mvps, mvp, JNI_ABORT);
    env->ReleaseFloatArrayElements(opacities, opa, JNI_ABORT);
    if (fx) env->ReleaseFloatArrayElements(effects, fx, JNI_ABORT);
    if (fxCounts) env->ReleaseIntArrayElements(effectCounts, fxCounts, JNI_ABORT);
    return layers;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeInit(JNIEnv*, jclass, jint w, jint h) {
    return Engine::instance().init(w, h) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeAttachPreview(
        JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;
    jboolean ok = Engine::instance().attachPreview(window) ? JNI_TRUE : JNI_FALSE;
    ANativeWindow_release(window);
    return ok;
}

JNIEXPORT void JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeDetachPreview(JNIEnv*, jclass) {
    Engine::instance().detachPreview();
}

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeRenderAt(
        JNIEnv* env, jclass, jobjectArray paths, jintArray clipKeys,
        jlongArray sourcePts, jfloatArray mvps, jfloatArray opacities,
        jfloatArray effects, jintArray effectCounts, jlong ptsUs) {
    std::vector<LayerRequest> layers = unmarshal(env, paths, clipKeys, sourcePts,
            mvps, opacities, effects, effectCounts);
    return Engine::instance().renderAt(layers, ptsUs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeInvalidate(JNIEnv*, jclass) {
    Engine::instance().invalidateTimeline();
}

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeStartExport(
        JNIEnv* env, jclass, jstring outputPath, jint w, jint h, jint frameRate) {
    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    jboolean ok = Engine::instance().startExport(path, w, h, frameRate) ? JNI_TRUE : JNI_FALSE;
    env->ReleaseStringUTFChars(outputPath, path);
    return ok;
}

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeExportFrame(
        JNIEnv* env, jclass, jobjectArray paths, jintArray clipKeys,
        jlongArray sourcePts, jfloatArray mvps, jfloatArray opacities,
        jfloatArray effects, jintArray effectCounts, jlong ptsUs) {
    std::vector<LayerRequest> layers = unmarshal(env, paths, clipKeys, sourcePts,
            mvps, opacities, effects, effectCounts);
    return Engine::instance().exportFrame(layers, ptsUs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeFinishExport(JNIEnv*, jclass) {
    return Engine::instance().finishExport() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeCancelExport(JNIEnv*, jclass) {
    Engine::instance().cancelExport();
}

JNIEXPORT jfloat JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeExportProgress(JNIEnv*, jclass) {
    return Engine::instance().exportProgress();
}

JNIEXPORT void JNICALL
Java_com_videomontage_nativecore_NativeEngine_nativeShutdown(JNIEnv*, jclass) {
    Engine::instance().shutdown();
}

} // extern "C"
