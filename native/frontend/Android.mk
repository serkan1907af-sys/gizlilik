LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := retrohost
LOCAL_SRC_FILES := host.c jni.c
LOCAL_CFLAGS    := -O2 -Wall -Wno-unused-parameter
LOCAL_LDLIBS    := -llog -ljnigraphics -ldl
include $(BUILD_SHARED_LIBRARY)
