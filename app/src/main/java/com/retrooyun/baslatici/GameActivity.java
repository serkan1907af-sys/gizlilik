package com.retrooyun.baslatici;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.view.Surface;
import android.content.Intent;
import android.util.SparseArray;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Secilen oyunu calistirir: motoru yukler, kareleri cizer, sesi calar, kumandayi motora iletir. */
public class GameActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_PATH = "path";

    private SurfaceView surface;
    private TouchOverlay overlay;
    private TextView loading;
    private volatile SurfaceHolder holder;
    private Thread loop;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private boolean dialogOpen = false;

    private String kind;
    private String path;

    // bluetooth / usb kumanda durumu
    private volatile int padButtons, padAxisButtons;
    private volatile int padLx, padLy, padRx, padRy;

    // ayarlar ve fiziksel tuslar
    private Settings st;
    private final SparseArray<String> held = new SparseArray<>(); // tus kodu -> islev
    private volatile int keyMask, keyStrafe;
    private InputManager inputManager;

    // harici klavye ve mouse
    private volatile int kbBits, kbDirs; // kbDirs: 1=ileri 2=geri 4=sol 8=sag
    private volatile float mouseDx, mouseDy;
    private volatile int mouseBits, wheelFrames, wheelBits;

    // jiroskop: telefonu cevirerek bakma / nisan alma
    private SensorManager sensorManager;
    private volatile boolean gyroOn = false;
    private volatile int gyroRx, gyroRy;
    private volatile float gravX, gravY, gravZ;
    private final SensorEventListener gravListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            if (e.sensor.getType() == Sensor.TYPE_GRAVITY) {
                gravX = e.values[0];
                gravY = e.values[1];
                gravZ = e.values[2];
            } else { // ivmeolcer: yumusatilmis yercekimi
                gravX = gravX * 0.9f + e.values[0] * 0.1f;
                gravY = gravY * 0.9f + e.values[1] * 0.1f;
                gravZ = gravZ * 0.9f + e.values[2] * 0.1f;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private float gyroSmoothX, gyroSmoothY;
    private int gyroRot = Surface.ROTATION_90;
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float wx = e.values[0], wy = e.values[1], wz = e.values[2];
            // Yatay donus: dunyanin dikey ekseni etrafinda donus hizi (yercekimi yonune gore).
            // Telefon nasil tutulursa tutulsun saga cevirince saga, sola cevirince sola doner.
            float gx = gravX, gy = gravY, gz = gravZ;
            float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            float turnRate;
            if (gl > 1f) turnRate = -(wx * gx + wy * gy + wz * gz) / gl; // saat yonu (yukaridan bakinca) = saga
            else turnRate = -wz;
            float gain = 12000f * Math.max(0.1f, st.geti("gyro.sens", 100) / 100f);
            float x = turnRate * gain * (st.getb("gyro.invx", false) ? -1f : 1f);
            if (Math.abs(turnRate) < 0.03f) x = 0;
            // Yukari-asagi bakma yalnizca ayarda acikca acilirsa
            float pitch = gyroRot == Surface.ROTATION_90 ? -wy : wy;
            float y = pitch * gain * (st.getb("gyro.invy", false) ? -1f : 1f);
            if (Math.abs(pitch) < 0.03f) y = 0;
            gyroSmoothX = gyroSmoothX * 0.5f + x * 0.5f;
            gyroSmoothY = gyroSmoothY * 0.5f + y * 0.5f;
            gyroRx = (int) Math.max(-32767f, Math.min(32767f, gyroSmoothX));
            gyroRy = Actions.quake(kind) && st.getb("gyro.pitch", false)
                    ? (int) Math.max(-32767f, Math.min(32767f, gyroSmoothY)) : 0;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private final InputManager.InputDeviceListener devListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { refreshPads(); }
        @Override public void onInputDeviceRemoved(int id) { refreshPads(); }
        @Override public void onInputDeviceChanged(int id) { refreshPads(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = Settings.load(this);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        path = getIntent().getStringExtra(EXTRA_PATH);
        if (kind == null || path == null) {
            finish();
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        overlay = new TouchOverlay(this);
        overlay.setKind(kind);
        overlay.setOnBackLong(this::onBackPressed);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        loading = new TextView(this);
        loading.setText("Yükleniyor…");
        loading.setTextColor(Color.WHITE);
        loading.setTextSize(18);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(loading, lp);

        setContentView(root);
        hideSystemUi();

        loop = new Thread(this::runLoop, "retro-loop");
        loop.start();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        paused = true;
        if (inputManager != null) inputManager.unregisterInputDeviceListener(devListener);
        if (sensorManager != null) {
            sensorManager.unregisterListener(gyroListener);
            sensorManager.unregisterListener(gravListener);
        }
        gyroRx = 0;
        gyroRy = 0;
        held.clear();
        keyMask = 0;
        keyStrafe = 0;
        freeMouse();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        st = Settings.load(this);
        if (overlay != null) overlay.reload();
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(devListener, null);
        refreshPads();
        gyroOn = st.getb("gyro2", false);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (gyroOn && sensorManager != null) {
            Sensor g = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (g != null) {
                gyroRot = getWindowManager().getDefaultDisplay().getRotation();
                sensorManager.registerListener(gyroListener, g, SensorManager.SENSOR_DELAY_GAME);
                Sensor gr = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (gr == null) gr = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (gr != null) sensorManager.registerListener(gravListener, gr, SensorManager.SENSOR_DELAY_GAME);
            } else {
                gyroOn = false;
            }
        }
        if (!dialogOpen) paused = false;
    }

    /** Kumanda bagliysa ekran tuslarini gizler (ayardan kapatilabilir). */
    private void refreshPads() {
        boolean any = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice dv = InputDevice.getDevice(id);
            if (dv == null || dv.isVirtual()) continue;
            int src = dv.getSources();
            if ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) any = true;
        }
        final boolean hide = any && st != null && st.getb("pad.hide", true);
        if (overlay != null) overlay.setPadHidden(hide);
    }

    /** Hizli ve donmadan cikis: dongu durdurulur, ekran kapanir, islem birazdan sonlandirilir. */
    private void quit() {
        running = false;
        paused = false;
        finish();
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Process.killProcess(Process.myPid());
        }, "quit").start();
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
        if (isFinishing()) {
            // Motoru beklemeden islemi kapat (motor kapanirken takilabiliyor).
            if (loop != null) {
                try {
                    loop.join(300);
                } catch (InterruptedException ignored) {
                }
            }
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        paused = true;
        dialogOpen = true;
        new AlertDialog.Builder(this)
                .setTitle("Duraklatıldı")
                .setItems(new String[]{"Devam", "Ayarlar", "Tuş yerleşimini düzenle", "Oyundan çık"}, (d, which) -> {
                    if (which == 1) {
                        startActivity(new Intent(this, SettingsActivity.class));
                    } else if (which == 2) {
                        startActivity(new Intent(this, LayoutEditorActivity.class).putExtra("kind", kind));
                    } else if (which == 3) {
                        new AlertDialog.Builder(this)
                                .setMessage("Oyundan çıkılsın mı?\n(Oyunun kendi menüsünden kaydetmediğin ilerleme kaybolur.)")
                                .setPositiveButton("Çık", (d2, w2) -> quit())
                                .setNegativeButton("Devam", null)
                                .show();
                    }
                })
                .setOnDismissListener(d -> {
                    dialogOpen = false;
                    paused = false;
                })
                .show();
    }

    // ---- surface ----
    @Override
    public void surfaceCreated(SurfaceHolder h) {
        holder = h;
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        holder = h;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        holder = null;
    }

    // ---- oyun dongusu (ayri is parcacigi) ----
    private void fail(String msg) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Oyun başlatılamadı")
                .setMessage(msg)
                .setPositiveButton("Tamam", (d, w) -> finish())
                .setCancelable(false)
                .show());
    }

    private AudioTrack makeAudio(int rate) {
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bytes = Math.max(min, rate / 8 * 4);
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    private static int pick(int a, int b) {
        return Math.abs(a) >= Math.abs(b) ? a : b;
    }

    private void runLoop() {
        AudioTrack audio = null;
        boolean coreLoaded = false;
        try {
            if (!Native.loadCore(Games.corePath(this, kind), Games.systemDir(this).getPath(),
                    Games.saveDir(this).getPath())) {
                fail("Motor yüklenemedi:\n" + Native.lastError());
                return;
            }
            coreLoaded = true;
            if (!Native.loadGame(path, Games.DEVICE_MODERN)) {
                fail("Oyun dosyası açılamadı:\n" + Native.lastError());
                return;
            }

            int rate = (int) Native.sampleRate();
            try {
                audio = makeAudio(rate);
                audio.play();
            } catch (Exception e) {
                audio = null; // sessiz devam
            }

            short[] abuf = new short[16384];
            Bitmap bmp = null;
            Paint paint = new Paint();
            paint.setFilterBitmap(false);
            long frameNs = (long) (1e9 / Math.max(20.0, Math.min(120.0, Native.fps())));
            long next = System.nanoTime();
            boolean first = true, wasPaused = false;

            while (running) {
                if (paused) {
                    if (!wasPaused) {
                        wasPaused = true;
                        if (audio != null) audio.pause();
                        Native.setInput(0, 0, 0, 0, 0);
                    }
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException ignored) {
                    }
                    next = System.nanoTime();
                    continue;
                }
                if (wasPaused) {
                    wasPaused = false;
                    if (audio != null) audio.play();
                }

                int rx = pick(overlay.rx, padRx);
                int ry = pick(overlay.ry, padRy);
                if (gyroOn) {
                    rx = (int) Math.max(-32767, Math.min(32767, rx + gyroRx));
                    ry = (int) Math.max(-32767, Math.min(32767, ry + gyroRy));
                }
                int lx = pick(overlay.lx, padLx);
                int ly = pick(overlay.ly, padLy);
                lx = (int) Math.max(-32767, Math.min(32767, lx + keyStrafe));
                int kd = kbDirs;
                if ((kd & 4) != 0) lx = (int) Math.max(-32767, lx - 32767);
                if ((kd & 8) != 0) lx = (int) Math.min(32767, lx + 32767);
                if ((kd & 1) != 0) ly = -32767;
                if ((kd & 2) != 0) ly = 32767;
                float mdx = mouseDx, mdy = mouseDy;
                mouseDx = 0;
                mouseDy = 0;
                if (mdx != 0 || mdy != 0) {
                    float mg = 1200f * Math.max(0.1f, st.geti("mouse.sens", 100) / 100f);
                    rx = (int) Math.max(-32767, Math.min(32767, rx + mdx * mg));
                    ry = (int) Math.max(-32767, Math.min(32767, ry + mdy * mg * (st.getb("mouse.invy", false) ? -1f : 1f)));
                }
                int wb = 0;
                if (wheelFrames > 0) {
                    wb = wheelBits;
                    wheelFrames--;
                }
                Native.setInput(overlay.buttons | padButtons | padAxisButtons | keyMask | kbBits | mouseBits | wb, lx, ly, rx, ry);
                Native.run();
                if (Native.shutdownRequested()) {
                    runOnUiThread(this::quit); // oyunun kendi menusunden "Quit Game"
                    running = false;
                    break;
                }

                if (audio != null) {
                    int n;
                    while ((n = Native.takeAudio(abuf)) > 0)
                        audio.write(abuf, 0, n * 2, AudioTrack.WRITE_NON_BLOCKING);
                }

                int w = Native.width(), h = Native.height();
                if (w > 0 && h > 0) {
                    if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h)
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    if (Native.copyFrame(bmp)) draw(bmp, paint);
                    if (first) {
                        first = false;
                        runOnUiThread(() -> loading.setVisibility(View.GONE));
                    }
                }

                next += frameNs;
                long wait = next - System.nanoTime();
                if (wait > 0) {
                    try {
                        Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
                    } catch (InterruptedException ignored) {
                    }
                } else if (wait < -frameNs * 5) {
                    next = System.nanoTime();
                }
            }
        } finally {
            if (audio != null) {
                try {
                    audio.stop();
                    audio.release();
                } catch (Exception ignored) {
                }
            }
            if (coreLoaded) Native.unload();
        }
    }

    private void draw(Bitmap bmp, Paint paint) {
        SurfaceHolder h = holder;
        if (h == null) return;
        Canvas c = null;
        try {
            c = Build.VERSION.SDK_INT >= 26 ? h.getSurface().lockHardwareCanvas() : h.lockCanvas();
        } catch (Exception e) {
            try {
                c = h.lockCanvas();
            } catch (Exception ignored) {
            }
        }
        if (c == null) return;
        try {
            int sw = c.getWidth(), sh = c.getHeight();
            float asp = Native.aspect();
            float dw = sw, dh = sw / asp;
            if (dh > sh) {
                dh = sh;
                dw = sh * asp;
            }
            int l = (int) ((sw - dw) / 2f), t = (int) ((sh - dh) / 2f);
            c.drawColor(Color.BLACK);
            c.drawBitmap(bmp, null, new Rect(l, t, l + (int) dw, t + (int) dh), paint);
        } finally {
            try {
                h.unlockCanvasAndPost(c);
            } catch (Exception ignored) {
            }
        }
    }

    // ---- kumanda (bluetooth / usb) ----
    private static int padBit(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return Actions.B;
            case KeyEvent.KEYCODE_BUTTON_B: return Actions.A;
            case KeyEvent.KEYCODE_BUTTON_X: return Actions.Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return Actions.X;
            case KeyEvent.KEYCODE_BUTTON_L1: return Actions.L;
            case KeyEvent.KEYCODE_BUTTON_R1: return Actions.R;
            case KeyEvent.KEYCODE_BUTTON_L2: return Actions.L2;
            case KeyEvent.KEYCODE_BUTTON_R2: return Actions.R2;
            case KeyEvent.KEYCODE_BUTTON_START: return Actions.START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return Actions.SELECT;
            case KeyEvent.KEYCODE_DPAD_UP: return Actions.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Actions.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Actions.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Actions.RIGHT;
            default: return 0;
        }
    }

    private void recomputeKeys() {
        int m = 0, sv = 0;
        for (int i = 0; i < held.size(); i++) {
            String a = held.valueAt(i);
            m |= Actions.mask(kind, a);
            if (a.equals("sl")) sv -= 32767;
            else if (a.equals("sr")) sv += 32767;
        }
        keyMask = m;
        keyStrafe = Math.max(-32767, Math.min(32767, sv));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        int action = e.getAction();
        if (code == KeyEvent.KEYCODE_BACK || (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP))
            return super.dispatchKeyEvent(e);

        // 1) Kullanicinin atadigi tuslar (ses tuslari, kumanda dugmeleri, klavye)
        if (action == KeyEvent.ACTION_DOWN) {
            if (held.indexOfKey(code) >= 0) return true; // tekrar eden basis
            String act = st.actionFor(kind, Settings.normalize(this, st, code));
            if (act != null) {
                held.put(code, act);
                int tk = Actions.keyFor(kind, act);
                if (tk != 0) Native.tapKey(tk);
                recomputeKeys();
                if (act.equals("menu") && overlay != null) overlay.toggleMenu();
                return true;
            }
        } else if (held.indexOfKey(code) >= 0) {
            held.remove(code);
            recomputeKeys();
            return true;
        }

        // 1b) Harici klavye: hazir esleme
        if (e.getDevice() != null && e.getDevice().getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                && (e.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
                && keyboardKey(e)) return true;

        // 2) Atanmamis kumanda dugmeleri: varsayilan eslestirme
        int src = e.getSource();
        boolean pad = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
        if (pad) {
            int bit = padBit(code);
            if (bit != 0) {
                if (action == KeyEvent.ACTION_DOWN) padButtons |= bit;
                else padButtons &= ~bit;
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void setBit(boolean down, int bit) {
        kbBits = down ? (kbBits | bit) : (kbBits & ~bit);
    }

    private void setDir(boolean down, int d) {
        kbDirs = down ? (kbDirs | d) : (kbDirs & ~d);
    }

    /** WASD yuruyus, oklar menu/yon, Enter tamam, Esc menu, bosluk kullan, Ctrl ates, 1-9 silah. */
    private boolean keyboardKey(KeyEvent e) {
        boolean dn = e.getAction() == KeyEvent.ACTION_DOWN;
        int c = e.getKeyCode();
        switch (c) {
            case KeyEvent.KEYCODE_W: setDir(dn, 1); return true;
            case KeyEvent.KEYCODE_S: setDir(dn, 2); return true;
            case KeyEvent.KEYCODE_A: setDir(dn, 4); return true;
            case KeyEvent.KEYCODE_D: setDir(dn, 8); return true;
            case KeyEvent.KEYCODE_DPAD_UP: setBit(dn, Actions.UP); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: setBit(dn, Actions.DOWN); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: setBit(dn, Actions.LEFT); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: setBit(dn, Actions.RIGHT); return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: setBit(dn, Actions.A); return true;
            case KeyEvent.KEYCODE_ESCAPE: setBit(dn, Actions.START); return true;
            case KeyEvent.KEYCODE_TAB: setBit(dn, Actions.SELECT); return true;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_E: setBit(dn, Actions.mask(kind, "use")); return true;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT: setBit(dn, Actions.R2); return true;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: setBit(dn, Actions.mask(kind, "run")); return true;
            default:
                if (c >= KeyEvent.KEYCODE_1 && c <= KeyEvent.KEYCODE_9) {
                    if (dn && e.getRepeatCount() == 0) Native.tapKey('1' + (c - KeyEvent.KEYCODE_1));
                    return true;
                }
                return false;
        }
    }

    // ---- mouse ----
    private boolean captured;

    @SuppressWarnings("NewApi")
    private void grabMouse() {
        if (Build.VERSION.SDK_INT < 26 || captured) return;
        surface.setFocusable(true);
        surface.setFocusableInTouchMode(true);
        surface.requestFocus();
        surface.setOnCapturedPointerListener((v, ev) -> {
            mouseDx += ev.getX();
            mouseDy += ev.getY();
            int b = ev.getButtonState();
            int bits = 0;
            if ((b & MotionEvent.BUTTON_PRIMARY) != 0) bits |= Actions.R2;
            if ((b & MotionEvent.BUTTON_SECONDARY) != 0) bits |= Actions.mask(kind, "use");
            mouseBits = bits;
            float sc = ev.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (sc != 0) {
                wheelBits = Actions.mask(kind, sc > 0 ? "prev" : "next");
                wheelFrames = 3;
            }
            return true;
        });
        surface.requestPointerCapture();
        captured = true;
    }

    @SuppressWarnings("NewApi")
    private void freeMouse() {
        if (Build.VERSION.SDK_INT >= 26 && surface != null) surface.releasePointerCapture();
        captured = false;
        mouseBits = 0;
    }

    @Override
    public void onPointerCaptureChanged(boolean has) {
        captured = has;
        if (!has) mouseBits = 0;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                && e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            grabMouse();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    private static int axis(MotionEvent e, int a) {
        float v = e.getAxisValue(a);
        return Math.abs(v) < 0.12f ? 0 : (int) (v * 32767f);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            grabMouse();
            return true;
        }
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && e.getAction() == MotionEvent.ACTION_MOVE) {
            padLx = axis(e, MotionEvent.AXIS_X);
            padLy = axis(e, MotionEvent.AXIS_Y);
            padRx = axis(e, MotionEvent.AXIS_Z);
            padRy = axis(e, MotionEvent.AXIS_RZ);
            float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            int b = 0;
            if (lt > 0.5f) b |= Actions.L2;
            if (rt > 0.5f) b |= Actions.R2;
            if (hx < -0.5f) b |= Actions.LEFT;
            if (hx > 0.5f) b |= Actions.RIGHT;
            if (hy < -0.5f) b |= Actions.UP;
            if (hy > 0.5f) b |= Actions.DOWN;
            padAxisButtons = b;
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }
}
