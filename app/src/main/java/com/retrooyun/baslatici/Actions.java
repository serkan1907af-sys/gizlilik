package com.retrooyun.baslatici;

/** Oyun islevleri (ates, kullan, silah...) ve bunlarin motordaki RetroPad karsiliklari. */
final class Actions {
    private Actions() {}

    // RetroPad bitleri (RETRO_DEVICE_ID_JOYPAD_* degerleriyle ayni)
    static final int B = 1, Y = 1 << 1, SELECT = 1 << 2, START = 1 << 3, UP = 1 << 4, DOWN = 1 << 5,
            LEFT = 1 << 6, RIGHT = 1 << 7, A = 1 << 8, X = 1 << 9, L = 1 << 10, R = 1 << 11,
            L2 = 1 << 12, R2 = 1 << 13;

    static boolean doom(String kind) {
        return Games.KIND_DOOM.equals(kind);
    }

    /** Tusa atanabilen islevler. */
    static String[] bindable(String kind) {
        if (doom(kind)) return new String[]{"fire", "use", "run", "next", "prev", "sl", "sr", "map", "menu"};
        return new String[]{"fire", "use", "next", "prev", "sl", "sr", "map", "menu"};
    }

    static String label(String kind, String act) {
        switch (act) {
            case "fire": return "Ateş";
            case "use": return doom(kind) ? "Aç / Kullan" : "Zıpla";
            case "run": return "Koş";
            case "next": return "Sonraki silah";
            case "prev": return "Önceki silah";
            case "sl": return "Sola yan yürü";
            case "sr": return "Sağa yan yürü";
            case "map": return doom(kind) ? "Harita" : "Skor tablosu";
            case "menu": return "Oyun menüsü";
            default: return "Yok";
        }
    }

    /** Islevin RetroPad maskesi. Yan yurume (sl/sr) cubuk ekseniyle yapildigi icin 0 doner. */
    static int mask(String kind, String act) {
        if (act == null) return 0;
        boolean d = doom(kind);
        switch (act) {
            case "fire": return R2;
            case "use": return d ? Y : L2;
            case "run": return d ? L2 : 0;
            case "next": return d ? A : R;
            case "prev": return d ? X : L;
            case "map": return SELECT;
            case "menu": return START;
            default: return 0;
        }
    }

    /** Cift dokunus icin secilebilen islevler. */
    static final String[] DT_IDS = {"none", "fire", "use", "run", "next", "prev", "map"};
    static final String[] DT_NAMES = {"Yok", "Ateş", "Kullan / Zıpla", "Koş", "Sonraki silah", "Önceki silah", "Harita / Skor"};

    // ---- ekran tuslari ----
    static String[] ctlIds(String kind) {
        if (doom(kind))
            return new String[]{"stick", "fire", "use", "run", "sl", "sr", "fire2", "prev", "next", "map", "menu"};
        return new String[]{"stick", "fire", "use", "sl", "sr", "fire2", "prev", "next", "map", "menu"};
    }

    static boolean isPill(String id) {
        return id.equals("prev") || id.equals("next") || id.equals("map") || id.equals("menu");
    }

    /** Ekran tusunun tetikledigi islev. */
    static String ctlAction(String id) {
        if (id.equals("fire2")) return "fire";
        if (id.equals("stick")) return null;
        return id;
    }

    static String ctlLabel(String kind, String id) {
        switch (id) {
            case "fire":
            case "fire2": return "ATEŞ";
            case "use": return doom(kind) ? "AÇ" : "ZIPLA";
            case "run": return "KOŞ";
            case "sl": return "‹YAN";
            case "sr": return "YAN›";
            case "prev": return "‹ SİLAH";
            case "next": return "SİLAH ›";
            case "map": return doom(kind) ? "HARİTA" : "SKOR";
            case "menu": return "MENÜ";
            default: return "ÇUBUK";
        }
    }

    static String ctlName(String kind, String id) {
        switch (id) {
            case "stick": return "Hareket çubuğu";
            case "fire2": return "Ateş (ikinci tuş)";
            default: return label(kind, id);
        }
    }

    static boolean ctlDefaultVisible(String kind, String id) {
        switch (id) {
            case "sl":
            case "sr": return false;
            case "fire2": return !doom(kind);
            default: return true;
        }
    }
}
