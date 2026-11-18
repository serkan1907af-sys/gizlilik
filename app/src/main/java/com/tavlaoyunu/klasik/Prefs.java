package com.tavlaoyunu.klasik;

import android.content.Context;
import android.content.SharedPreferences;

/** Ayarlar, istatistikler, haklar ve Pro durumu (telefonda saklanır, hesap gerekmez). */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() {}

    public static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("tavla", Context.MODE_PRIVATE);
    }

    // ---- ayarlar ----
    public static boolean sound() { return sp.getBoolean("sound", true); }
    public static void setSound(boolean v) { sp.edit().putBoolean("sound", v).apply(); }
    public static boolean vibration() { return sp.getBoolean("vibr", true); }
    public static void setVibration(boolean v) { sp.edit().putBoolean("vibr", v).apply(); }
    public static boolean shake() { return sp.getBoolean("shake", true); }
    public static void setShake(boolean v) { sp.edit().putBoolean("shake", v).apply(); }
    public static int theme() { return sp.getInt("theme", 0); }
    public static void setTheme(int v) { sp.edit().putInt("theme", v).apply(); }
    public static int lastLevel() { return sp.getInt("level", 2); }
    public static void setLastLevel(int v) { sp.edit().putInt("level", v).apply(); }

    // ---- Pro ve haklar ----
    public static boolean pro() { return sp.getBoolean("pro", false); }
    public static void setPro(boolean v) { sp.edit().putBoolean("pro", v).apply(); }
    public static int credits() { return sp.getInt("credits", 0); }
    public static void addCredits(int n) { sp.edit().putInt("credits", credits() + n).apply(); }
    public static boolean spendCredit() {
        int c = credits();
        if (c <= 0) return false;
        sp.edit().putInt("credits", c - 1).apply();
        return true;
    }

    // ---- istatistik ----
    public static int games() { return sp.getInt("games", 0); }
    public static int wins() { return sp.getInt("wins", 0); }
    public static int gammons() { return sp.getInt("gammons", 0); }
    public static int streak() { return sp.getInt("streak", 0); }
    public static int bestStreak() { return sp.getInt("bestStreak", 0); }
    public static int gamesAt(int level) { return sp.getInt("games_l" + level, 0); }
    public static int winsAt(int level) { return sp.getInt("wins_l" + level, 0); }
    public static int face(int player, int f) { return sp.getInt("face_" + player + "_" + f, 0); }
    public static int doubles(int player) { return sp.getInt("dbl_" + player, 0); }
    public static int rolls(int player) { return sp.getInt("rolls_" + player, 0); }

    public static void recordGame(int level, boolean humanWon, boolean gammon,
                                  int[][] faceCount, int[] doubles, int[] rollCount) {
        SharedPreferences.Editor e = sp.edit();
        e.putInt("games", games() + 1);
        e.putInt("games_l" + level, gamesAt(level) + 1);
        if (humanWon) {
            e.putInt("wins", wins() + 1);
            e.putInt("wins_l" + level, winsAt(level) + 1);
            if (gammon) e.putInt("gammons", gammons() + 1);
            int s = streak() + 1;
            e.putInt("streak", s);
            if (s > bestStreak()) e.putInt("bestStreak", s);
        } else {
            e.putInt("streak", 0);
        }
        for (int p = 0; p < 2; p++) {
            for (int f = 1; f <= 6; f++) {
                e.putInt("face_" + p + "_" + f, face(p, f) + faceCount[p][f]);
            }
            e.putInt("dbl_" + p, doubles(p) + doubles[p]);
            e.putInt("rolls_" + p, rolls(p) + rollCount[p]);
        }
        e.apply();
    }

    // ---- oturum içi sayaçlar (kalıcı değil) ----
    public static int finishedThisSession = 0;
}
