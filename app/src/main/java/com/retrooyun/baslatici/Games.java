package com.retrooyun.baslatici;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Dosya yollari, oyun turleri ve ilk calistirma hazirligi. */
final class Games {
    private Games() {}

    static final String KIND_DOOM = "doom";
    static final String KIND_QUAKE = "quake";
    static final String KIND_HERETIC = "heretic";
    static final String KIND_HEXEN = "hexen";

    /** WAD dosya adindan oyun turunu bulur (Heretic / Hexen / Doom). */
    static String kindForWad(String fileName) {
        String n = fileName.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("heretic")) return KIND_HERETIC;
        if (n.contains("hexen") || n.contains("hexdd")) return KIND_HEXEN;
        return KIND_DOOM;
    }

    /** RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 2): iki analog cubuklu "modern" kumanda duzeni. */
    static final int DEVICE_MODERN = (3 << 8) | 5;

    /** Uygulamaya gomulu, ucretsiz ve serbestce dagitilabilen oyun. */
    static final String BUNDLED_WAD = "freedoom1.wad";

    static File doomDir(Context c) {
        return new File(c.getFilesDir(), "games/doom");
    }

    static File quakeId1(Context c) {
        return new File(c.getFilesDir(), "games/quake/id1");
    }

    static File quakePak0(Context c) {
        return new File(quakeId1(c), "pak0.pak");
    }

    static File systemDir(Context c) {
        return new File(c.getFilesDir(), "system");
    }

    static File saveDir(Context c) {
        return new File(c.getFilesDir(), "saves");
    }

    static String corePath(Context c, String kind) {
        String name = KIND_QUAKE.equals(kind) ? "libtyrquake_libretro.so" : "libprboom_libretro.so";
        return c.getApplicationInfo().nativeLibraryDir + "/" + name;
    }

    static boolean quakeInstalled(Context c) {
        return quakePak0(c).isFile();
    }

    /** Ilk acilista: motorun kendi veri dosyasini ve gomulu Freedoom'u hazirlar. */
    static void ensureAssets(Context c) throws IOException {
        systemDir(c).mkdirs();
        new File(systemDir(c), "prboom").mkdirs();
        saveDir(c).mkdirs();
        doomDir(c).mkdirs();

        AssetManager am = c.getAssets();
        copyAssetIfMissing(am, "prboom.wad", new File(systemDir(c), "prboom.wad"));
        copyAssetIfMissing(am, "prboom.wad", new File(systemDir(c), "prboom/prboom.wad"));
        copyAssetIfMissing(am, BUNDLED_WAD, new File(doomDir(c), BUNDLED_WAD));
    }

    private static void copyAssetIfMissing(AssetManager am, String asset, File out) throws IOException {
        if (out.isFile() && out.length() > 0) return;
        File tmp = new File(out.getPath() + ".tmp");
        try (InputStream in = am.open(asset); OutputStream os = new FileOutputStream(tmp)) {
            copy(in, os);
        }
        if (!tmp.renameTo(out)) throw new IOException("rename failed: " + out);
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
