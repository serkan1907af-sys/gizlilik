package com.parabolsoft.retrokonsol;

import android.content.Context;

import java.io.File;
import java.util.Locale;

/** Desteklenen konsollar: dosya uzantilari, motor ve tuslar. */
final class Sys {
    final String id, name, shortName, core;
    final String[] exts;
    final String[] buttons; // bu konsolda olan RetroPad tuslari

    private Sys(String id, String name, String shortName, String core, String[] exts, String[] buttons) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.core = core;
        this.exts = exts;
        this.buttons = buttons;
    }

    static final Sys NES = new Sys("nes", "NES", "NES", "fceumm",
            new String[]{"nes", "unf", "unif"},
            new String[]{"up", "down", "left", "right", "b", "a", "select", "start"});
    static final Sys SNES = new Sys("snes", "SNES", "SNES", "bsnes",
            new String[]{"sfc", "smc", "swc", "fig"},
            new String[]{"up", "down", "left", "right", "b", "a", "y", "x", "l", "r", "select", "start"});
    static final Sys GB = new Sys("gb", "Game Boy / Color", "GB", "gambatte",
            new String[]{"gb", "gbc"},
            new String[]{"up", "down", "left", "right", "b", "a", "select", "start"});
    static final Sys GBA = new Sys("gba", "Game Boy Advance", "GBA", "vbanext",
            new String[]{"gba"},
            new String[]{"up", "down", "left", "right", "b", "a", "l", "r", "select", "start"});

    static final Sys[] ALL = {NES, SNES, GB, GBA};

    static Sys byId(String id) {
        if (id == null) return null;
        for (Sys s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    static String extOf(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** Dosya adindan konsolu bulur (uzantiya gore); desteklenmiyorsa null. */
    static Sys forFile(String fileName) {
        String e = extOf(fileName);
        for (Sys s : ALL) for (String x : s.exts) if (x.equals(e)) return s;
        return null;
    }

    boolean has(String button) {
        for (String b : buttons) if (b.equals(button)) return true;
        return false;
    }

    String extList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < exts.length; i++) {
            if (i > 0) sb.append("  ");
            sb.append('.').append(exts[i]);
        }
        return sb.toString();
    }

    String corePath(Context c) {
        return c.getApplicationInfo().nativeLibraryDir + "/lib" + core + "_libretro.so";
    }

    File romDir(Context c) {
        return new File(c.getFilesDir(), "roms/" + id);
    }

    static File saveDir(Context c) {
        return new File(c.getFilesDir(), "saves");
    }

    static File stateDir(Context c) {
        return new File(c.getFilesDir(), "states");
    }

    static File cheatDir(Context c) {
        return new File(c.getFilesDir(), "cheats");
    }

    static File systemDir(Context c) {
        return new File(c.getFilesDir(), "system");
    }

    static void ensureDirs(Context c) {
        for (Sys s : ALL) s.romDir(c).mkdirs();
        saveDir(c).mkdirs();
        stateDir(c).mkdirs();
        cheatDir(c).mkdirs();
        systemDir(c).mkdirs();
    }
}
