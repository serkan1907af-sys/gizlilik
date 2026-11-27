package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Oyun ekrani: emulasyon is parcacigi, goruntu, ses, giris (dokunma / klavye / kumanda), menu. */
public class GameActivity extends Activity implements PadView.Listener {
    static final String EXTRA_SYS = "sys", EXTRA_ROM = "rom";

    static void launch(Context c, RomLib.Rom r) {
        Intent i = new Intent(c, GameActivity.class);
        i.putExtra(EXTRA_SYS, r.sys.id);
        i.putExtra(EXTRA_ROM, r.file.getAbsolutePath());
        c.startActivity(i);
    }

    private Sys sys;
    private File romFile;
    private String key;
    private SurfaceView surface;
    private PadView pad;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // emulasyon is parcacigi ile paylasilan durum
    private Thread emu;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean quit, paused = true, resumed;
    private volatile int dialogs;
    private volatile boolean pro;
    private volatile int keyMask, axisMask;
    private volatile boolean ffToggle, ffKey, rewKey;
    private volatile String vmode = "fit", vfilter = "none";
    private volatile boolean vsmooth = false;
    private volatile int volume = 100;
    private boolean proNagShown;

    // ------------------------------------------------------------ yasam dongusu
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sys = Sys.byId(getIntent().getStringExtra(EXTRA_SYS));
        String p = getIntent().getStringExtra(EXTRA_ROM);
        if (sys == null || p == null || !new File(p).isFile()) {
            Toast.makeText(this, "Oyun bulunamadı.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        romFile = new File(p);
        key = RomLib.key(sys, romFile);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        pad = new PadView(this);
        pad.setSystem(sys);
        pad.setListener(this);
        root.addView(pad, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        Settings st = Settings.load(this);
        st.setl("last." + key, System.currentTimeMillis());
        st.save();

        emu = new Thread(this::emuMain, "emu");
        emu.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sys == null) return;
        pro = Pro.has(this);
        Settings st = Settings.load(this);
        String o = st.gets("orient", "auto");
        setRequestedOrientation("land".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : "port".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        applyVideoSettings();
        pad.reload();
        immersive();
        resumed = true;
        updatePause();
    }

    @Override
    protected void onPause() {
        resumed = false;
        updatePause();
        if (sys != null && emu != null && emu.isAlive()) {
            runSync(() -> {
                Native.flushSaves();
                if (Settings.load(this).getb("autoresume", true)) Native.saveState(autoFile().getPath());
            }, 1500);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        quit = true;
        if (emu != null) {
            try {
                emu.join(2500);
            } catch (InterruptedException ignored) {
            }
        }
        super.onDestroy();
        // Cekirdekler temiz baslasin diye oyun islemini tamamen kapat.
        if (sys != null) Process.killProcess(Process.myPid());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private void immersive() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void updatePause() {
        paused = !resumed || dialogs > 0;
    }

    private void applyVideoSettings() {
        Settings st = Settings.load(this);
        vmode = st.gets("video.mode", "fit");
        vsmooth = st.getb("video.smooth", false);
        String f = st.gets("video.filter", "none");
        vfilter = pro ? f : "none";
        volume = Math.max(0, Math.min(100, st.geti("volume", 100)));
    }

    private File autoFile() {
        return new File(Sys.stateDir(this), key + ".auto");
    }

    private File slotFile(int slot) {
        return new File(Sys.stateDir(this), key + ".s" + slot);
    }

    private void toast(String m) {
        ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    /** Emulasyon is parcaciginda calistirir ve (en fazla ms kadar) bekler. */
    private void runSync(Runnable r, long ms) {
        CountDownLatch l = new CountDownLatch(1);
        queue.add(() -> {
            try {
                r.run();
            } finally {
                l.countDown();
            }
        });
        try {
            l.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------ emulasyon is parcacigi
    private void setOptions() {
        if (sys == Sys.GB) {
            Native.setOption("gambatte_gb_colorization", "auto");
        }
    }

    private List<String[]> loadCheats() {
        List<String[]> out = new ArrayList<>(); // {acik, ad, kod}
        File f = new File(Sys.cheatDir(this), key + ".txt");
        if (!f.isFile()) return out;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", 3);
                if (p.length == 3) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveCheats(List<String[]> l) {
        try (FileOutputStream os = new FileOutputStream(new File(Sys.cheatDir(this), key + ".txt"))) {
            StringBuilder sb = new StringBuilder();
            for (String[] c : l) sb.append(c[0]).append('|').append(c[1]).append('|').append(c[2]).append('\n');
            os.write(sb.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private void applyCheatsNative(List<String[]> l) {
        Native.cheatReset();
        if (!pro) return;
        for (int i = 0; i < l.size(); i++) Native.cheatSet(i, "1".equals(l.get(i)[0]), l.get(i)[2]);
    }

    private void emuMain() {
        AudioTrack track = null;
        try {
            Sys.ensureDirs(this);
            setOptions();
            if (!Native.loadCore(sys.corePath(this), Sys.systemDir(this).getPath(), Sys.saveDir(this).getPath())) {
                fail("Oyun motoru yüklenemedi: " + Native.lastError());
                return;
            }
            if (!Native.loadGame(romFile.getPath())) {
                fail("Oyun açılamadı: " + Native.lastError());
                Native.unload();
                return;
            }
            Native.loadSaves(new File(Sys.saveDir(this), key).getPath());
            boolean fast = sys == Sys.NES || sys == Sys.GB;
            if (pro) Native.rewindEnable(fast ? 16 : 64, fast ? 2 : 4);
            applyCheatsNative(loadCheats());
            if (Settings.load(this).getb("autoresume", true) && autoFile().isFile()) {
                Native.loadState(autoFile().getPath());
            }

            double fps = Native.fps();
            if (fps < 20 || fps > 200) fps = 60;
            final long frameNs = (long) (1e9 / fps);
            int rate = (int) Native.sampleRate();
            if (rate < 8000 || rate > 96000) rate = 44100;
            try {
                int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                int buf = Math.max(min, rate / 8 * 4);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(buf)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                track.play();
            } catch (Throwable t) {
                track = null;
            }

            short[] abuf = new short[8192 * 2];
            Bitmap bmp = null;
            Paint paint = new Paint();
            Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlay.setColor(Color.WHITE);
            overlay.setTextSize(15 * getResources().getDisplayMetrics().density);
            overlay.setShadowLayer(3, 0, 0, Color.BLACK);
            Paint line = new Paint();
            long next = System.nanoTime();
            boolean wasPaused = false;
            long lastFlush = System.nanoTime();
            int lastVol = -1;

            while (!quit) {
                Runnable r;
                while ((r = queue.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                    }
                }
                if (paused) {
                    if (!wasPaused && track != null) {
                        try {
                            track.pause();
                            track.flush();
                        } catch (Throwable ignored) {
                        }
                    }
                    wasPaused = true;
                    drawFrame(bmp, paint, line, overlay, null);
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {
                    }
                    next = System.nanoTime();
                    continue;
                }
                if (wasPaused && track != null) {
                    try {
                        track.play();
                    } catch (Throwable ignored) {
                    }
                }
                wasPaused = false;
                if (track != null && lastVol != volume) {
                    lastVol = volume;
                    try {
                        track.setVolume(volume / 100f);
                    } catch (Throwable ignored) {
                    }
                }

                boolean rew = pro && (pad.rewind || rewKey);
                boolean ff = pro && (ffToggle || ffKey);
                Native.setInput(pad.buttons | keyMask | axisMask);
                String status = null;
                boolean advanced = true;
                if (rew) {
                    advanced = Native.rewindStep();
                    status = "◀◀";
                } else if (ff) {
                    for (int i = 0; i < 3; i++) Native.run();
                    status = "▶▶";
                } else {
                    Native.run();
                }

                // ses
                int n;
                boolean play = !rew && !ff && track != null;
                while ((n = Native.takeAudio(abuf)) > 0) {
                    if (play) track.write(abuf, 0, n * 2);
                }

                // goruntu
                if (advanced) {
                    int w = Native.width(), h = Native.height();
                    if (w > 0 && h > 0) {
                        if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) {
                            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        }
                        Native.copyFrame(bmp);
                    }
                }
                drawFrame(bmp, paint, line, overlay, status);

                // pil kayitlari her ~5 sn
                long now = System.nanoTime();
                if (now - lastFlush > 5_000_000_000L) {
                    lastFlush = now;
                    Native.flushSaves();
                }

                // hiz ayari
                next += frameNs;
                long d = next - now;
                if (d > 1_000_000L) {
                    try {
                        Thread.sleep(d / 1_000_000L, (int) (d % 1_000_000L));
                    } catch (InterruptedException ignored) {
                    }
                } else if (d < -100_000_000L) {
                    next = now;
                }
            }
        } catch (Throwable t) {
            fail("Beklenmeyen hata: " + t);
        } finally {
            try {
                Native.flushSaves();
                Native.unload();
            } catch (Throwable ignored) {
            }
            if (track != null) {
                try {
                    track.stop();
                    track.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void fail(String msg) {
        ui.post(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void drawFrame(Bitmap bmp, Paint paint, Paint line, Paint overlay, String status) {
        SurfaceHolder h = surface.getHolder();
        if (h == null || h.getSurface() == null || !h.getSurface().isValid()) return;
        Canvas c = null;
        try {
            c = h.lockCanvas();
            if (c == null) return;
            c.drawColor(Color.BLACK);
            if (bmp == null) return;
            int sw = c.getWidth(), sh = c.getHeight();
            int w = Native.width(), hh = Native.height();
            if (w <= 0 || hh <= 0 || w > bmp.getWidth() || hh > bmp.getHeight()) {
                w = bmp.getWidth();
                hh = bmp.getHeight();
            }
            float asp = Native.aspect();
            if (asp < 0.3f || asp > 4f) asp = (float) w / hh;
            float d = getResources().getDisplayMetrics().density;
            RectF dst = PadView.videoRect(sw, sh, asp, w, hh, vmode, 16 * d);
            if ("integer".equals(vmode)) {
                dst = new RectF(Math.round(dst.left), Math.round(dst.top), Math.round(dst.right), Math.round(dst.bottom));
            }
            paint.setFilterBitmap(vsmooth);
            c.drawBitmap(bmp, new Rect(0, 0, w, hh), dst, paint);

            float sx = dst.width() / w, sy = dst.height() / hh;
            if ("crt".equals(vfilter) && sy >= 2f) {
                line.setColor(0x66000000);
                for (int y = 0; y < hh; y++) {
                    float t = dst.top + y * sy;
                    c.drawRect(dst.left, t + sy * 0.62f, dst.right, t + sy, line);
                }
            } else if ("lcd".equals(vfilter) && sx >= 3f && sy >= 3f) {
                line.setColor(0x38000000);
                for (int y = 1; y < hh; y++) {
                    float t = dst.top + y * sy;
                    c.drawRect(dst.left, t - 0.5f, dst.right, t + 0.5f, line);
                }
                for (int x = 1; x < w; x++) {
                    float t = dst.left + x * sx;
                    c.drawRect(t - 0.5f, dst.top, t + 0.5f, dst.bottom, line);
                }
            }
            if (status != null) c.drawText(status, dst.left + 10 * d, dst.top + 22 * d, overlay);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                try {
                    h.unlockCanvasAndPost(c);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------ ekran tuslari olaylari
    @Override
    public void onMenu() {
        ui.post(this::showMenu);
    }

    @Override
    public void onFfToggle() {
        ffToggle = !ffToggle;
        pad.setFfActive(ffToggle);
    }

    @Override
    public void onLocked(String what) {
        ui.post(() -> showPro(what));
    }

    // ------------------------------------------------------------ giris (klavye / kumanda)
    private String defaultAction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_BUTTON_A: return "b";
            case KeyEvent.KEYCODE_BUTTON_B: return "a";
            case KeyEvent.KEYCODE_BUTTON_X: return "y";
            case KeyEvent.KEYCODE_BUTTON_Y: return "x";
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2: return "l";
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2: return "r";
            case KeyEvent.KEYCODE_BUTTON_START: return "start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "select";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "menu";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "rew";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "ff";
            // klavye
            case KeyEvent.KEYCODE_Z: return "b";
            case KeyEvent.KEYCODE_X: return "a";
            case KeyEvent.KEYCODE_A: return "y";
            case KeyEvent.KEYCODE_S: return "x";
            case KeyEvent.KEYCODE_Q: return "l";
            case KeyEvent.KEYCODE_W: return "r";
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return "start";
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "select";
            case KeyEvent.KEYCODE_SPACE: return "ff";
            case KeyEvent.KEYCODE_DEL: return "rew";
            case KeyEvent.KEYCODE_ESCAPE: return "menu";
            case KeyEvent.KEYCODE_F5: return "qsave";
            case KeyEvent.KEYCODE_F7: return "qload";
            default: return null;
        }
    }

    private void nagPro() {
        if (proNagShown) return;
        proNagShown = true;
        ui.post(() -> {
            Pro.init(this, () -> ui.post(() -> {
                pro = Pro.has(this);
                pad.reload();
            }));
            showPro("Kumanda ve klavye desteği");
        });
    }

    private void usedExternal() {
        if (Settings.load(this).getb("pad.autohide", true)) pad.setPadHidden(true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (sys == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) ui.post(this::showMenu);
            return true;
        }
        Settings st = pad.settings();
        String act = st.actionFor(sys, code);
        if (act == null) act = defaultAction(code);
        if (act == null) return super.dispatchKeyEvent(e);
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // ses tuslari yalnizca kullanici atadiysa yakalanir
            if (st.actionFor(sys, code) == null) return super.dispatchKeyEvent(e);
        }
        if (!pro) {
            if (down && e.getRepeatCount() == 0) nagPro();
            return true;
        }
        if (e.getRepeatCount() > 0 && down) return true;
        usedExternal();
        int m = Actions.mask(act);
        if (m != 0) {
            keyMask = down ? (keyMask | m) : (keyMask & ~m);
            return true;
        }
        switch (act) {
            case "ff": ffKey = down; break;
            case "rew": rewKey = down; break;
            case "qsave": if (down) quickState(true); break;
            case "qload": if (down) quickState(false); break;
            case "menu": if (down) ui.post(this::showMenu); break;
            default: break;
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        int src = ev.getSource();
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!pro) {
                float mx = Math.max(Math.abs(ev.getAxisValue(MotionEvent.AXIS_X)), Math.abs(ev.getAxisValue(MotionEvent.AXIS_Y)));
                if (mx > 0.6f) nagPro();
                return true;
            }
            float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X), hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
            float lx = ev.getAxisValue(MotionEvent.AXIS_X), ly = ev.getAxisValue(MotionEvent.AXIS_Y);
            float lt = Math.max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt = Math.max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS));
            int m = 0;
            if (hx < -0.5f || lx < -0.5f) m |= Actions.LEFT;
            if (hx > 0.5f || lx > 0.5f) m |= Actions.RIGHT;
            if (hy < -0.5f || ly < -0.5f) m |= Actions.UP;
            if (hy > 0.5f || ly > 0.5f) m |= Actions.DOWN;
            if (lt > 0.5f) m |= Actions.L;
            if (rt > 0.5f) m |= Actions.R;
            if (m != 0) usedExternal();
            axisMask = m;
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) pad.setPadHidden(false);
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------ kayit noktalari
    private void quickState(boolean save) {
        int slot = pro ? Math.max(1, Math.min(9, Settings.load(this).geti("slot", 1))) : 1;
        if (save) doSave(slot);
        else doLoad(slot);
    }

    private void doSave(int slot) {
        File f = slotFile(slot);
        Settings s = Settings.load(this);
        s.seti("slot", slot);
        s.save();
        queue.add(() -> {
            Native.flushSaves();
            boolean ok = Native.saveState(f.getPath());
            toast(ok ? "Kaydedildi (yuva " + slot + ")" : "Kaydedilemedi");
        });
    }

    private void doLoad(int slot) {
        File f = slotFile(slot);
        if (!f.isFile()) {
            toast("Yuva " + slot + " boş");
            return;
        }
        Settings s = Settings.load(this);
        s.seti("slot", slot);
        s.save();
        queue.add(() -> {
            boolean ok = Native.loadState(f.getPath());
            toast(ok ? "Yüklendi (yuva " + slot + ")" : "Yüklenemedi");
        });
    }

    // ------------------------------------------------------------ menuler
    private AlertDialog.Builder builder() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private AlertDialog showDlg(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        dialogs++;
        updatePause();
        d.setOnDismissListener(x -> {
            dialogs--;
            updatePause();
            immersive();
        });
        d.show();
        return d;
    }

    private void showPro(String what) {
        dialogs++;
        updatePause();
        if (!pro) Pro.init(this, () -> ui.post(() -> {
            pro = Pro.has(this);
            pad.reload();
        }));
        Pro.show(this, what, () -> {
            dialogs--;
            updatePause();
            pro = Pro.has(this);
            pad.reload();
            immersive();
        });
    }

    private void showMenu() {
        if (isFinishing()) return;
        final String[] items = {
                "▶  Devam et",
                "💾  Kaydet…",
                "📂  Yükle…",
                "↺  Oyunu sıfırla",
                "🔑  Hile kodları" + (pro ? "" : "  🔒"),
                "🖥  Ekran ayarları",
                "🎮  Tuş düzenini düzenle" + (pro ? "" : "  🔒"),
                "⚙  Ayarlar",
                "⏏  Oyundan çık"
        };
        showDlg(builder().setTitle(sys.name).setItems(items, (d, w) -> {
            switch (w) {
                case 1: slotDialog(true); break;
                case 2: slotDialog(false); break;
                case 3: confirmReset(); break;
                case 4:
                    if (pro) cheatDialog();
                    else showPro("Hile kodları");
                    break;
                case 5: displayMenu(); break;
                case 6:
                    if (pro) startActivity(new Intent(this, LayoutEditorActivity.class).putExtra(EXTRA_SYS, sys.id));
                    else showPro("Tuş düzeni düzenleyicisi");
                    break;
                case 7: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 8: finish(); break;
                default: break;
            }
        }));
    }

    private void confirmReset() {
        showDlg(builder().setTitle("Oyun sıfırlansın mı?")
                .setMessage("Kaydedilmemiş ilerleme kaybolur.")
                .setPositiveButton("Sıfırla", (d, w) -> queue.add(Native::reset))
                .setNegativeButton("Vazgeç", null));
    }

    private void slotDialog(boolean save) {
        String[] items = new String[9];
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (int i = 0; i < 9; i++) {
            File f = slotFile(i + 1);
            String t = "Yuva " + (i + 1) + "  —  " + (f.isFile() ? df.format(new Date(f.lastModified())) : "boş");
            if (i > 0 && !pro) t += "  🔒";
            items[i] = t;
        }
        showDlg(builder().setTitle(save ? "Nereye kaydedilsin?" : "Hangi yuvadan yüklensin?")
                .setItems(items, (d, w) -> {
                    if (w > 0 && !pro) {
                        showPro("9 kayıt yuvası");
                        return;
                    }
                    if (save) doSave(w + 1);
                    else doLoad(w + 1);
                })
                .setNegativeButton("Geri", (d, w) -> showMenu()));
    }

    private void displayMenu() {
        Settings st = Settings.load(this);
        String mode = st.gets("video.mode", "fit");
        boolean smooth = st.getb("video.smooth", false);
        String filter = st.gets("video.filter", "none");
        String[] items = {
                "Ekran boyutu:  " + ("integer".equals(mode) ? "Tam sayı ölçek (keskin)" : "stretch".equals(mode) ? "Ekranı doldur (uzat)" : "Oranı koru"),
                "Görüntü:  " + (smooth ? "Yumuşak" : "Keskin"),
                "Filtre:  " + ("crt".equals(filter) ? "CRT" : "lcd".equals(filter) ? "LCD" : "Yok") + (pro ? "" : "  🔒")
        };
        showDlg(builder().setTitle("Ekran ayarları").setItems(items, (d, w) -> {
            Settings s = Settings.load(this);
            if (w == 0) {
                s.sets("video.mode", "fit".equals(mode) ? "integer" : "integer".equals(mode) ? "stretch" : "fit");
            } else if (w == 1) {
                s.setb("video.smooth", !smooth);
            } else {
                if (!pro) {
                    showPro("Ekran filtreleri");
                    return;
                }
                s.sets("video.filter", "none".equals(filter) ? "crt" : "crt".equals(filter) ? "lcd" : "none");
            }
            s.save();
            applyVideoSettings();
            ui.post(this::displayMenu);
        }).setNegativeButton("Geri", (d, w) -> showMenu()));
    }

    private void cheatDialog() {
        final List<String[]> list = loadCheats();
        String[] names = new String[list.size()];
        boolean[] on = new boolean[list.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = list.get(i)[1] + "   (" + list.get(i)[2] + ")";
            on[i] = "1".equals(list.get(i)[0]);
        }
        AlertDialog.Builder b = builder().setTitle("Hile kodları");
        if (list.isEmpty()) {
            b.setMessage("Henüz hile yok. “Yeni ekle” ile bir kod gir.\n\n"
                    + "Kod biçimi: " + cheatHint() + "\n\nSilmek için bir hileye uzun bas.");
        } else {
            b.setMultiChoiceItems(names, on, (d, i, checked) -> on[i] = checked);
        }
        b.setPositiveButton("Uygula", (d, w) -> {
            for (int i = 0; i < list.size(); i++) list.get(i)[0] = on[i] ? "1" : "0";
            saveCheats(list);
            queue.add(() -> applyCheatsNative(list));
            toast("Hileler uygulandı");
        });
        b.setNeutralButton("Yeni ekle", (d, w) -> addCheatDialog());
        b.setNegativeButton("Geri", (d, w) -> showMenu());
        AlertDialog dlg = showDlg(b);
        if (!list.isEmpty()) {
            dlg.getListView().setOnItemLongClickListener((p, v, pos, id) -> {
                showDlg(builder().setTitle("Hile silinsin mi?").setMessage(list.get(pos)[1])
                        .setPositiveButton("Sil", (d2, w2) -> {
                            list.remove(pos);
                            saveCheats(list);
                            queue.add(() -> applyCheatsNative(list));
                            dlg.dismiss();
                            ui.post(this::cheatDialog);
                        })
                        .setNegativeButton("Vazgeç", null));
                return true;
            });
        }
    }

    private String cheatHint() {
        switch (sys.id) {
            case "nes": return "Game Genie (örn. SXIOPO) ya da ADRES:DEĞER";
            case "snes": return "Game Genie (DF93-6D0F) ya da Pro Action Replay (7E0DBF99)";
            case "gb": return "Game Genie (00A-17B-C4E) ya da GameShark (010FF6C1)";
            default: return "GameShark / Action Replay kodu";
        }
    }

    private void addCheatDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        EditText name = new EditText(this);
        name.setHint("Adı (örn. Sınırsız can)");
        EditText code = new EditText(this);
        code.setHint("Kod");
        box.addView(name);
        box.addView(code);
        showDlg(builder().setTitle("Yeni hile").setView(box)
                .setMessage(cheatHint())
                .setPositiveButton("Ekle", (d, w) -> {
                    String n = name.getText().toString().replace("|", " ").trim();
                    String c = code.getText().toString().replace("|", " ").replace("\n", " ").trim();
                    if (c.isEmpty()) {
                        toast("Kod boş olamaz");
                    } else {
                        List<String[]> l = loadCheats();
                        l.add(new String[]{"1", n.isEmpty() ? "Hile " + (l.size() + 1) : n, c});
                        saveCheats(l);
                        queue.add(() -> applyCheatsNative(l));
                    }
                    ui.post(this::cheatDialog);
                })
                .setNegativeButton("Vazgeç", (d, w) -> ui.post(this::cheatDialog)));
    }
}
