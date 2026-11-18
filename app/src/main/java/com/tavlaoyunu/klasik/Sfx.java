package com.tavlaoyunu.klasik;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Ses efektleri ve titreşim. */
public final class Sfx {
    private static SoundPool pool;
    private static int sDice, sLand, sChecker, sHit, sWin, sLose;
    private static Vibrator vib;

    private Sfx() {}

    public static void init(Context c) {
        if (pool != null) return;
        Context app = c.getApplicationContext();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build();
        sDice = pool.load(app, R.raw.dice_roll, 1);
        sLand = pool.load(app, R.raw.dice_land, 1);
        sChecker = pool.load(app, R.raw.checker, 1);
        sHit = pool.load(app, R.raw.hit, 1);
        sWin = pool.load(app, R.raw.win, 1);
        sLose = pool.load(app, R.raw.lose, 1);
        vib = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static void play(int id) {
        if (pool != null && Prefs.sound()) pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    private static void buzz(long ms) {
        if (vib == null || !Prefs.vibration()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(ms);
            }
        } catch (Exception ignored) {
        }
    }

    public static void diceRoll() { play(sDice); buzz(40); }
    public static void diceLand() { play(sLand); buzz(25); }
    public static void checker() { play(sChecker); buzz(12); }
    public static void hit() { play(sHit); buzz(60); }
    public static void win() { play(sWin); buzz(120); }
    public static void lose() { play(sLose); }
}
