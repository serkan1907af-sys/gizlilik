#include <android/bitmap.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "host.h"

#define JNI(name) Java_com_retrooyun_baslatici_Native_##name

JNIEXPORT jboolean JNICALL JNI(loadCore)(JNIEnv *env, jclass c, jstring core, jstring sys, jstring save)
{
    const char *a = (*env)->GetStringUTFChars(env, core, NULL);
    const char *b = (*env)->GetStringUTFChars(env, sys, NULL);
    const char *d = (*env)->GetStringUTFChars(env, save, NULL);
    int ok = host_load_core(a, b, d);
    (*env)->ReleaseStringUTFChars(env, core, a);
    (*env)->ReleaseStringUTFChars(env, sys, b);
    (*env)->ReleaseStringUTFChars(env, save, d);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI(loadGame)(JNIEnv *env, jclass c, jstring path, jint device)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int ok = host_load_game(p, (unsigned)device);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI(setInput)(JNIEnv *env, jclass c, jint buttons, jint lx, jint ly, jint rx, jint ry)
{
    host_set_input((uint32_t)buttons, lx, ly, rx, ry);
}

JNIEXPORT void JNICALL JNI(run)(JNIEnv *env, jclass c) { host_run(); }
JNIEXPORT void JNICALL JNI(unload)(JNIEnv *env, jclass c) { host_unload(); }
JNIEXPORT jint JNICALL JNI(width)(JNIEnv *env, jclass c) { return host_width(); }
JNIEXPORT jint JNICALL JNI(height)(JNIEnv *env, jclass c) { return host_height(); }
JNIEXPORT jfloat JNICALL JNI(aspect)(JNIEnv *env, jclass c) { return host_aspect(); }
JNIEXPORT jdouble JNICALL JNI(fps)(JNIEnv *env, jclass c) { return host_fps(); }
JNIEXPORT jdouble JNICALL JNI(sampleRate)(JNIEnv *env, jclass c) { return host_sample_rate(); }
JNIEXPORT jint JNICALL JNI(menuOpen)(JNIEnv *env, jclass c) { return host_menu_open(); }
JNIEXPORT void JNICALL JNI(tapKey)(JNIEnv *env, jclass c, jint code) { host_tap_key((unsigned)code); }
JNIEXPORT jboolean JNICALL JNI(shutdownRequested)(JNIEnv *env, jclass c) { return host_shutdown_requested() ? JNI_TRUE : JNI_FALSE; }
JNIEXPORT jint JNICALL JNI(frameCount)(JNIEnv *env, jclass c) { return host_frame_count(); }

JNIEXPORT jstring JNICALL JNI(lastError)(JNIEnv *env, jclass c)
{
    return (*env)->NewStringUTF(env, host_last_error());
}

/* Bitmap must be ARGB_8888 and at least host_width() x host_height(). */
JNIEXPORT jboolean JNICALL JNI(copyFrame)(JNIEnv *env, jclass c, jobject bitmap)
{
    AndroidBitmapInfo info;
    void *pixels = NULL;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if ((int)info.width < host_width() || (int)info.height < host_height()) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) return JNI_FALSE;
    host_copy_rgba((uint32_t *)pixels, (int)(info.stride / 4));
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI(takeAudio)(JNIEnv *env, jclass c, jshortArray arr)
{
    jsize len = (*env)->GetArrayLength(env, arr);
    jshort *p = (*env)->GetShortArrayElements(env, arr, NULL);
    if (!p) return 0;
    int n = host_take_audio((int16_t *)p, len / 2);
    (*env)->ReleaseShortArrayElements(env, arr, p, 0);
    return n;
}
