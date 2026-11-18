package com.retrooyun.baslatici;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekran uzerindeki dokunmatik kontroller.
 * Sol taraf: hareket cubugu. Sag taraf: parmagi surukleyerek etrafa bakma. Dugmeler: ates, kullan vb.
 * "MENU" tusuna basinca oyunun kendi menusu icin yon tuslari gosterilir.
 */
public class TouchOverlay extends View {
    // RetroPad bitleri (RETRO_DEVICE_ID_JOYPAD_* degerleriyle ayni)
    static final int B = 1, Y = 1 << 1, SELECT = 1 << 2, START = 1 << 3, UP = 1 << 4, DOWN = 1 << 5,
            LEFT = 1 << 6, RIGHT = 1 << 7, A = 1 << 8, X = 1 << 9, L = 1 << 10, R = 1 << 11,
            L2 = 1 << 12, R2 = 1 << 13;

    private static final int MODE_GAME = 0, MODE_MENU = 1;
    private static final int ACT_NONE = 0, ACT_OPEN_MENU = 1, ACT_CLOSE_MENU = 2;

    private static final class Ctl {
        String label;
        int mask, mode, act;
        boolean round, pressed;
        float cx, cy, r;
        final RectF rect = new RectF();
    }

    private final List<Ctl> controls = new ArrayList<>();
    private final SparseArray<Ctl> pointerCtl = new SparseArray<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final Object lookLock = new Object();

    private String kind = Games.KIND_DOOM;
    private int mode = MODE_GAME;

    private int stickId = -1;
    private float sx0, sy0, sx, sy;
    private int lookId = -1;
    private float lastX, lastY, lookDx, lookDy;

    /** Oyun is parcaciginin okudugu durum. */
    public volatile int buttons;
    public volatile int lx, ly;

    public TouchOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setFocusable(false);
    }

    public void setKind(String k) {
        kind = k;
        buildControls();
        invalidate();
    }

    /** Son kareden beri biriken parmak surukleme miktari (piksel): {dx, dy}. */
    public float[] consumeLook() {
        synchronized (lookLock) {
            float[] r = {lookDx, lookDy};
            lookDx = 0;
            lookDy = 0;
            return r;
        }
    }

    private Ctl add(boolean round, String label, int mask, int mode, int act, float cx, float cy, float r) {
        Ctl c = new Ctl();
        c.round = round;
        c.label = label;
        c.mask = mask;
        c.mode = mode;
        c.act = act;
        c.cx = cx;
        c.cy = cy;
        c.r = r;
        if (!round) c.rect.set(cx - 42 * d, cy - 20 * d, cx + 42 * d, cy + 20 * d);
        controls.add(c);
        return c;
    }

    private void buildControls() {
        controls.clear();
        pointerCtl.clear();
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        boolean doom = Games.KIND_DOOM.equals(kind);

        // ---- oyun kontrolleri ----
        add(true, "ATEŞ", R2, MODE_GAME, ACT_NONE, w - 95 * d, h - 100 * d, 54 * d);
        if (doom) {
            add(true, "AÇ", Y, MODE_GAME, ACT_NONE, w - 200 * d, h - 62 * d, 38 * d);
            add(true, "KOŞ", L2, MODE_GAME, ACT_NONE, w - 62 * d, h - 205 * d, 36 * d);
        } else {
            add(true, "ZIPLA", L2, MODE_GAME, ACT_NONE, w - 200 * d, h - 62 * d, 38 * d);
        }
        float cx = w / 2f, ty = 30 * d;
        add(false, "‹ SİLAH", doom ? X : L, MODE_GAME, ACT_NONE, cx - 138 * d, ty, 0);
        add(false, "MENÜ", START, MODE_GAME, ACT_OPEN_MENU, cx - 46 * d, ty, 0);
        add(false, "SİLAH ›", doom ? A : R, MODE_GAME, ACT_NONE, cx + 46 * d, ty, 0);
        add(false, doom ? "HARİTA" : "SKOR", SELECT, MODE_GAME, ACT_NONE, cx + 138 * d, ty, 0);

        // ---- menu kontrolleri ----
        float mx = 150 * d, my = h - 130 * d;
        add(true, "▲", UP, MODE_MENU, ACT_NONE, mx, my - 62 * d, 32 * d);
        add(true, "▼", DOWN, MODE_MENU, ACT_NONE, mx, my + 62 * d, 32 * d);
        add(true, "◀", LEFT, MODE_MENU, ACT_NONE, mx - 62 * d, my, 32 * d);
        add(true, "▶", RIGHT, MODE_MENU, ACT_NONE, mx + 62 * d, my, 32 * d);
        add(true, "TAMAM", A, MODE_MENU, ACT_NONE, w - 95 * d, h - 110 * d, 50 * d);
        add(true, "GERİ", B, MODE_MENU, ACT_NONE, w - 205 * d, h - 70 * d, 38 * d);
        add(false, "KAPAT", START, MODE_MENU, ACT_CLOSE_MENU, cx - 46 * d, ty, 0);
        add(false, "OYUN", 0, MODE_MENU, ACT_CLOSE_MENU, cx + 46 * d, ty, 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        buildControls();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private boolean hit(Ctl c, float x, float y) {
        if (c.round) {
            float dx = x - c.cx, dy = y - c.cy, rr = c.r * 1.15f;
            return dx * dx + dy * dy <= rr * rr;
        }
        return x >= c.rect.left - 6 * d && x <= c.rect.right + 6 * d
                && y >= c.rect.top - 6 * d && y <= c.rect.bottom + 6 * d;
    }

    private void releaseAll() {
        for (Ctl c : controls) c.pressed = false;
        pointerCtl.clear();
        stickId = -1;
        lookId = -1;
        publish();
    }

    private void down(int id, float x, float y) {
        for (int i = controls.size() - 1; i >= 0; i--) {
            Ctl c = controls.get(i);
            if (c.mode != mode || !hit(c, x, y)) continue;
            c.pressed = true;
            pointerCtl.put(id, c);
            if (c.act == ACT_OPEN_MENU) mode = MODE_MENU;
            else if (c.act == ACT_CLOSE_MENU) mode = MODE_GAME;
            return;
        }
        if (mode != MODE_GAME) return;
        if (x < getWidth() * 0.45f) {
            if (stickId < 0) {
                stickId = id;
                sx0 = sx = x;
                sy0 = sy = y;
            }
        } else if (lookId < 0) {
            lookId = id;
            lastX = x;
            lastY = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == stickId) {
            sx = x;
            sy = y;
        } else if (id == lookId) {
            synchronized (lookLock) {
                lookDx += x - lastX;
                lookDy += y - lastY;
            }
            lastX = x;
            lastY = y;
        }
    }

    private void up(int id) {
        Ctl c = pointerCtl.get(id);
        if (c != null) {
            c.pressed = false;
            pointerCtl.remove(id);
        }
        if (id == stickId) stickId = -1;
        if (id == lookId) lookId = -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        publish();
        invalidate();
        return true;
    }

    private void publish() {
        int b = 0;
        for (Ctl c : controls) if (c.pressed) b |= c.mask;
        buttons = b;
        if (stickId >= 0) {
            float R = 70 * d;
            float dx = (sx - sx0) / R, dy = (sy - sy0) / R;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m > 1f) {
                dx /= m;
                dy /= m;
            }
            lx = (int) (dx * 32767);
            ly = (int) (dy * 32767);
        } else {
            lx = 0;
            ly = 0;
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        stroke.setColor(0xA0FFFFFF);
        for (Ctl c : controls) {
            if (c.mode != mode) continue;
            fill.setColor(c.pressed ? 0x78FFFFFF : 0x30FFFFFF);
            text.setColor(0xDDFFFFFF);
            if (c.round) {
                cv.drawCircle(c.cx, c.cy, c.r, fill);
                cv.drawCircle(c.cx, c.cy, c.r, stroke);
                text.setTextSize(Math.min(c.r * 0.42f, 20 * d));
                float ty = c.cy - (text.ascent() + text.descent()) / 2f;
                cv.drawText(c.label, c.cx, ty, text);
            } else {
                cv.drawRoundRect(c.rect, 14 * d, 14 * d, fill);
                cv.drawRoundRect(c.rect, 14 * d, 14 * d, stroke);
                text.setTextSize(13 * d);
                float ty = c.rect.centerY() - (text.ascent() + text.descent()) / 2f;
                cv.drawText(c.label, c.rect.centerX(), ty, text);
            }
        }
        if (mode == MODE_GAME) {
            float R = 70 * d;
            if (stickId >= 0) {
                float dx = sx - sx0, dy = sy - sy0;
                float m = (float) Math.sqrt(dx * dx + dy * dy);
                if (m > R) {
                    dx = dx / m * R;
                    dy = dy / m * R;
                }
                fill.setColor(0x24FFFFFF);
                cv.drawCircle(sx0, sy0, R, fill);
                cv.drawCircle(sx0, sy0, R, stroke);
                fill.setColor(0x80FFFFFF);
                cv.drawCircle(sx0 + dx, sy0 + dy, 28 * d, fill);
            } else {
                stroke.setColor(0x40FFFFFF);
                cv.drawCircle(120 * d, getHeight() - 110 * d, R, stroke);
            }
        }
    }
}
