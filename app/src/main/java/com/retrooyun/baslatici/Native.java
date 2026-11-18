package com.retrooyun.baslatici;

import android.graphics.Bitmap;

/** libretro ana programi (native/frontend). Hepsi TEK bir is parcaciginden cagrilmali. */
public final class Native {
    static {
        System.loadLibrary("retrohost");
    }

    private Native() {}

    public static native boolean loadCore(String corePath, String systemDir, String saveDir);

    public static native boolean loadGame(String path, int device);

    public static native void setInput(int buttons, int lx, int ly, int rx, int ry);

    public static native void run();

    public static native void unload();

    public static native int width();

    public static native int height();

    public static native float aspect();

    public static native double fps();

    public static native double sampleRate();

    public static native int frameCount();

    public static native String lastError();

    public static native boolean copyFrame(Bitmap bitmap);

    public static native int takeAudio(short[] buffer);
}
