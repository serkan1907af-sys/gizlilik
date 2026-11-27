package com.parabolsoft.retrokonsol;

import android.graphics.Bitmap;

/** libretro ana programi (native/frontend). Hepsi TEK bir is parcaciginden cagrilmali. */
public final class Native {
    static {
        System.loadLibrary("retrohost");
    }

    private Native() {}

    /** Cekirdek ayari (retro_variable). loadCore'dan ONCE cagrilmali. */
    public static native void setOption(String key, String value);

    public static native boolean loadCore(String corePath, String systemDir, String saveDir);

    public static native boolean loadGame(String path);

    public static native void setInput(int buttons);

    public static native void run();

    public static native void reset();

    public static native void unload();

    public static native int width();

    public static native int height();

    public static native float aspect();

    public static native double fps();

    public static native double sampleRate();

    public static native int frameCount();

    /** Pil kaydini (.srm / .rtc) okur; base = klasor + oyun anahtari. */
    public static native void loadSaves(String base);

    /** Degisen pil kayitlarini diske yazar; kac dosya yazildigini dondurur. */
    public static native int flushSaves();

    public static native int stateSize();

    public static native boolean saveState(String path);

    public static native boolean loadState(String path);

    public static native void rewindEnable(int megabytes, int everyFrames);

    public static native boolean rewindStep();

    public static native int rewindCount();

    public static native void cheatReset();

    public static native void cheatSet(int index, boolean enabled, String code);

    public static native String lastError();

    public static native boolean copyFrame(Bitmap bitmap);

    public static native int takeAudio(short[] buffer);
}
