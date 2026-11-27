package com.parabolsoft.retrokonsol;

import java.util.ArrayList;
import java.util.List;

/** Oyun islevleri (A, B, Start...) ve RetroPad karsiliklari; ekran tuslarinin listesi. */
final class Actions {
    private Actions() {}

    // RetroPad bitleri (RETRO_DEVICE_ID_JOYPAD_* degerleriyle ayni)
    static final int B = 1, Y = 1 << 1, SELECT = 1 << 2, START = 1 << 3, UP = 1 << 4, DOWN = 1 << 5,
            LEFT = 1 << 6, RIGHT = 1 << 7, A = 1 << 8, X = 1 << 9, L = 1 << 10, R = 1 << 11;

    static int mask(String act) {
        if (act == null) return 0;
        switch (act) {
            case "b": return B;
            case "y": return Y;
            case "select": return SELECT;
            case "start": return START;
            case "up": return UP;
            case "down": return DOWN;
            case "left": return LEFT;
            case "right": return RIGHT;
            case "a": return A;
            case "x": return X;
            case "l": return L;
            case "r": return R;
            default: return 0;
        }
    }

    /** Tusa atanabilen islevler: konsolun tuslari + ozel islevler. */
    static List<String> bindable(Sys s) {
        List<String> l = new ArrayList<>();
        for (String b : s.buttons) l.add(b);
        l.add("ff");
        l.add("rew");
        l.add("qsave");
        l.add("qload");
        l.add("menu");
        return l;
    }

    static String label(String act) {
        switch (act) {
            case "up": return "Yukarı";
            case "down": return "Aşağı";
            case "left": return "Sol";
            case "right": return "Sağ";
            case "a": return "A";
            case "b": return "B";
            case "x": return "X";
            case "y": return "Y";
            case "l": return "L";
            case "r": return "R";
            case "select": return "Select";
            case "start": return "Start";
            case "ff": return "Hızlandır (basılı tut)";
            case "rew": return "Geri sar (basılı tut)";
            case "qsave": return "Hızlı kaydet";
            case "qload": return "Hızlı yükle";
            case "menu": return "Menü";
            default: return act;
        }
    }

    // ---- ekran tuslari ----
    static List<String> ctlIds(Sys s) {
        List<String> l = new ArrayList<>();
        l.add("dpad");
        for (String b : s.buttons) {
            if (b.equals("up") || b.equals("down") || b.equals("left") || b.equals("right")) continue;
            l.add(b);
        }
        l.add("menu");
        l.add("ff");
        l.add("rew");
        return l;
    }

    static boolean isPill(String id) {
        switch (id) {
            case "l":
            case "r":
            case "select":
            case "start":
            case "menu":
            case "ff":
            case "rew": return true;
            default: return false;
        }
    }

    static String ctlLabel(String id) {
        switch (id) {
            case "a": return "A";
            case "b": return "B";
            case "x": return "X";
            case "y": return "Y";
            case "l": return "L";
            case "r": return "R";
            case "select": return "SELECT";
            case "start": return "START";
            case "menu": return "MENÜ";
            case "ff": return "▶▶";
            case "rew": return "◀◀";
            default: return "";
        }
    }

    static String ctlName(String id) {
        switch (id) {
            case "dpad": return "Yön tuşları";
            case "ff": return "Hızlandır düğmesi";
            case "rew": return "Geri sar düğmesi";
            case "menu": return "Menü düğmesi";
            default: return label(id) + " düğmesi";
        }
    }
}
