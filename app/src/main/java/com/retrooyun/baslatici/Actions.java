package com.retrooyun.baslatici;

/** Oyun islevleri (ates, kullan, silah...) ve bunlarin motordaki RetroPad / klavye karsiliklari. */
final class Actions {
    private Actions() {}

    // RetroPad bitleri (RETRO_DEVICE_ID_JOYPAD_* degerleriyle ayni)
    static final int B = 1, Y = 1 << 1, SELECT = 1 << 2, START = 1 << 3, UP = 1 << 4, DOWN = 1 << 5,
            LEFT = 1 << 6, RIGHT = 1 << 7, A = 1 << 8, X = 1 << 9, L = 1 << 10, R = 1 << 11,
            L2 = 1 << 12, R2 = 1 << 13;

    static final String[] ALL_KINDS = {Games.KIND_DOOM, Games.KIND_HERETIC, Games.KIND_HEXEN, Games.KIND_QUAKE};

    static boolean quake(String kind) {
        return Games.KIND_QUAKE.equals(kind);
    }

    static boolean heretic(String kind) {
        return Games.KIND_HERETIC.equals(kind);
    }

    static boolean hexen(String kind) {
        return Games.KIND_HEXEN.equals(kind);
    }

    static String kindName(String kind) {
        switch (kind) {
            case Games.KIND_HERETIC: return "Heretic";
            case Games.KIND_HEXEN: return "Hexen";
            case Games.KIND_QUAKE: return "Quake";
            default: return "Doom";
        }
    }

    /** Sayi tuslariyla secilebilen silah sayisi. */
    static int weaponCount(String kind) {
        if (quake(kind)) return 8;
        if (hexen(kind)) return 4;
        return 7;
    }

    /** Tusa atanabilen islevler. */
    static String[] bindable(String kind) {
        if (quake(kind)) return new String[]{"fire", "use", "next", "prev", "sl", "sr", "map", "menu"};
        if (hexen(kind))
            return new String[]{"fire", "use", "run", "next", "prev", "jump", "item", "inv_l", "inv_r",
                    "fly_up", "fly_dn", "sl", "sr", "map", "menu"};
        if (heretic(kind))
            return new String[]{"fire", "use", "run", "next", "prev", "item", "inv_l", "inv_r", "sl", "sr", "map", "menu"};
        return new String[]{"fire", "use", "run", "next", "prev", "sl", "sr", "map", "menu"};
    }

    static String label(String kind, String act) {
        switch (act) {
            case "fire": return "Ateş";
            case "use": return quake(kind) ? "Zıpla" : "Aç / Kullan";
            case "run": return "Koş";
            case "next": return "Sonraki silah";
            case "prev": return "Önceki silah";
            case "sl": return "Sola yan yürü";
            case "sr": return "Sağa yan yürü";
            case "map": return quake(kind) ? "Skor tablosu" : "Harita";
            case "menu": return "Oyun menüsü";
            case "jump": return "Zıpla";
            case "item": return "Eşyayı kullan";
            case "inv_l": return "Önceki eşya";
            case "inv_r": return "Sonraki eşya";
            case "fly_up": return "Yüksel (uçma)";
            case "fly_dn": return "Alçal (uçma)";
            default: return "Yok";
        }
    }

    /** Islevin RetroPad maskesi. Yan yurume cubuk ekseniyle, bazi islevler klavye tusuyla yapilir: 0 doner. */
    static int mask(String kind, String act) {
        if (act == null) return 0;
        switch (act) {
            case "fire": return R2;
            case "map": return SELECT;
            case "menu": return START;
            default:
        }
        if (quake(kind)) {
            switch (act) {
                case "use": return L2;
                case "next": return R;
                case "prev": return L;
                default: return 0;
            }
        }
        if (hexen(kind)) {
            switch (act) {
                case "use": return Y;
                case "run": return L2;
                case "next": return RIGHT;
                case "prev": return LEFT;
                case "jump": return A;
                case "item": return X;
                case "inv_l": return L;
                case "inv_r": return R;
                case "fly_up": return UP;
                case "fly_dn": return DOWN;
                default: return 0;
            }
        }
        if (heretic(kind)) {
            switch (act) {
                case "use": return Y;
                case "run": return B;
                case "next": return R;
                case "prev": return L;
                default: return 0;
            }
        }
        switch (act) { // Doom
            case "use": return Y;
            case "run": return L2;
            case "next": return A;
            case "prev": return X;
            default: return 0;
        }
    }

    /** Islev klavye tusuyla yapiliyorsa RETROK kodu (kisa basis), yoksa 0. */
    static int keyFor(String kind, String act) {
        if (act != null && heretic(kind)) {
            switch (act) {
                case "item": return 'q';
                case "inv_l": return 'c';
                case "inv_r": return 'v';
                default: return 0;
            }
        }
        return 0;
    }

    /** Cift dokunus icin secilebilen islevler. */
    static final String[] DT_IDS = {"none", "fire", "use", "run", "next", "prev", "map"};
    static final String[] DT_NAMES = {"Yok", "Ateş", "Kullan / Zıpla", "Koş", "Sonraki silah", "Önceki silah", "Harita / Skor"};

    // ---- ekran tuslari ----
    static String[] ctlIds(String kind) {
        if (quake(kind))
            return new String[]{"stick", "fire", "use", "sl", "sr", "fire2", "prev", "next", "map", "menu", "wpn", "kbd", "dp_up", "dp_down", "dp_left", "dp_right"};
        if (hexen(kind))
            return new String[]{"stick", "fire", "use", "run", "jump", "sl", "sr", "fire2", "prev", "next",
                    "inv_l", "item", "inv_r", "map", "menu", "wpn", "kbd", "dp_up", "dp_down", "dp_left", "dp_right"};
        if (heretic(kind))
            return new String[]{"stick", "fire", "use", "run", "sl", "sr", "fire2", "prev", "next",
                    "inv_l", "item", "inv_r", "map", "menu", "wpn", "kbd", "dp_up", "dp_down", "dp_left", "dp_right"};
        return new String[]{"stick", "fire", "use", "run", "sl", "sr", "fire2", "prev", "next", "map", "menu", "wpn", "kbd", "dp_up", "dp_down", "dp_left", "dp_right"};
    }

    static boolean isPill(String id) {
        switch (id) {
            case "prev":
            case "next":
            case "map":
            case "menu":
            case "inv_l":
            case "inv_r":
            case "item":
            case "kbd": return true;
            default: return false;
        }
    }

    /** Ekran tusunun tetikledigi islev. */
    static String ctlAction(String id) {
        if (id.equals("fire2")) return "fire";
        if (id.equals("stick") || id.equals("wpn") || id.equals("kbd") || id.startsWith("dp_")) return null;
        return id;
    }

    static String ctlLabel(String kind, String id) {
        switch (id) {
            case "fire":
            case "fire2": return "ATEŞ";
            case "use": return quake(kind) ? "ZIPLA" : "AÇ";
            case "run": return "KOŞ";
            case "jump": return "ZIPLA";
            case "sl": return "‹YAN";
            case "sr": return "YAN›";
            case "prev": return "‹ SİLAH";
            case "next": return "SİLAH ›";
            case "map": return quake(kind) ? "SKOR" : "HARİTA";
            case "menu": return "MENÜ";
            case "inv_l": return "‹ EŞYA";
            case "inv_r": return "EŞYA ›";
            case "item": return "KULLAN";
            case "kbd": return "KLAVYE";
            case "dp_up": return "▲";
            case "dp_down": return "▼";
            case "dp_left": return "◀";
            case "dp_right": return "▶";
            case "wpn": return "SİLAH";
            default: return "ÇUBUK";
        }
    }

    static String ctlName(String kind, String id) {
        switch (id) {
            case "stick": return "Hareket çubuğu";
            case "fire2": return "Ateş (ikinci tuş)";
            case "kbd": return "Klavye düğmesi";
            case "dp_up": return "Yön tuşu ▲ (ileri / menüde yukarı)";
            case "dp_down": return "Yön tuşu ▼ (geri / menüde aşağı)";
            case "dp_left": return "Yön tuşu ◀ (sola dön)";
            case "dp_right": return "Yön tuşu ▶ (sağa dön)";
            case "wpn": return "Silah seçme şeridi (1, 2, 3…)";
            default: return label(kind, id);
        }
    }

    static boolean ctlDefaultVisible(String kind, String id) {
        switch (id) {
            case "sl":
            case "sr": return false;
            case "fire2": return quake(kind);
            default: return true;
        }
    }
}
