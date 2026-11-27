#include <android/bitmap.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "host.h"

#define JNI(name) Java_com_parabolsoft_retrokonsol_Native_##name

JNIEXPORT void JNICALL JNI(setOption)(JNIEnv *env, jclass c, jstring key, jstring val)
{
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = (*env)->GetStringUTFChars(env, val, NULL);
    host_set_option(k, v);
    (*env)->ReleaseStringUTFChars(env, key, k);
    (*env)->ReleaseStringUTFChars(env, val, v);
}

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

JNIEXPORT jboolean JNICALL JNI(loadGame)(JNIEnv *env, jclass c, jstring path)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int ok = host_load_game(p);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI(setInput)(JNIEnv *env, jclass c, jint buttons) { host_set_input((uint32_t)buttons); }
JNIEXPORT void JNICALL JNI(run)(JNIEnv *env, jclass c) { host_run(); }
JNIEXPORT void JNICALL JNI(reset)(JNIEnv *env, jclass c) { host_reset(); }
JNIEXPORT void JNICALL JNI(unload)(JNIEnv *env, jclass c) { host_unload(); }
JNIEXPORT jint JNICALL JNI(width)(JNIEnv *env, jclass c) { return host_width(); }
JNIEXPORT jint JNICALL JNI(height)(JNIEnv *env, jclass c) { return host_height(); }
JNIEXPORT jfloat JNICALL JNI(aspect)(JNIEnv *env, jclass c) { return host_aspect(); }
JNIEXPORT jdouble JNICALL JNI(fps)(JNIEnv *env, jclass c) { return host_fps(); }
JNIEXPORT jdouble JNICALL JNI(sampleRate)(JNIEnv *env, jclass c) { return host_sample_rate(); }
JNIEXPORT jint JNICALL JNI(frameCount)(JNIEnv *env, jclass c) { return host_frame_count(); }

JNIEXPORT void JNICALL JNI(loadSaves)(JNIEnv *env, jclass c, jstring base)
{
    const char *b = (*env)->GetStringUTFChars(env, base, NULL);
    host_load_saves(b);
    (*env)->ReleaseStringUTFChars(env, base, b);
}

JNIEXPORT jint JNICALL JNI(flushSaves)(JNIEnv *env, jclass c) { return host_flush_saves(); }
JNIEXPORT jint JNICALL JNI(stateSize)(JNIEnv *env, jclass c) { return host_state_size(); }

JNIEXPORT jboolean JNICALL JNI(saveState)(JNIEnv *env, jclass c, jstring path)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int ok = host_save_state(p);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI(loadState)(JNIEnv *env, jclass c, jstring path)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int ok = host_load_state(p);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI(rewindEnable)(JNIEnv *env, jclass c, jint mb, jint every) { host_rewind_enable(mb, every); }
JNIEXPORT jboolean JNICALL JNI(rewindStep)(JNIEnv *env, jclass c) { return host_rewind_step() ? JNI_TRUE : JNI_FALSE; }
JNIEXPORT jint JNICALL JNI(rewindCount)(JNIEnv *env, jclass c) { return host_rewind_count(); }

JNIEXPORT void JNICALL JNI(cheatReset)(JNIEnv *env, jclass c) { host_cheat_reset(); }

JNIEXPORT void JNICALL JNI(cheatSet)(JNIEnv *env, jclass c, jint index, jboolean enabled, jstring code)
{
    const char *s = (*env)->GetStringUTFChars(env, code, NULL);
    host_cheat_set((unsigned)index, enabled ? 1 : 0, s);
    (*env)->ReleaseStringUTFChars(env, code, s);
}

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
