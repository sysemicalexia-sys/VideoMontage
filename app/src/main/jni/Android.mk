LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := montage_engine

LOCAL_SRC_FILES := \
    jni_bridge.cpp \
    engine.cpp \
    vm_decoder.cpp \
    vm_renderer.cpp \
    vm_encoder.cpp \
    frame_pool.cpp \
    frame_cache.cpp \
    pcm_processor.cpp \
    compositor.cpp \
    gl_utils.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)

LOCAL_LDLIBS := -llog -landroid -lmediandk -lGLESv3 -lEGL -lOpenSLES

include $(BUILD_SHARED_LIBRARY)
