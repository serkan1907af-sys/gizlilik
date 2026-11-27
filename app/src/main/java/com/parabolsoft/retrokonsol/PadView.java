package com.parabolsoft.retrokonsol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekran uzerindeki dokunmatik kumanda: yon tuslari, A/B/X/Y, L/R, Start/Select, menu, hizlandir, geri sar.
 * Parmak bir tustan digerine kaydirilabilir, ayni anda cok parmak calisir.
 * Duzenleme modunda tuslar surukleyerek yerlestirilir (dikey ve yatay ekran ayri kaydedilir).
 */
public class PadView extends View {
    interface Listener {
        void onMenu();

        void onFfToggle();

        void onLocked(String what);
    }

    interface EditListener {
        void onSelected(String name, boolean visible);
    }

    private static final int U = 1, D = 2, L = 4, R = 8;

    private static final class Ctl {
        String id, label;
        boolean dpad, pill, visible = true, locked, down;
        int mask, dirs;
        float cx, cy, r, baseR, scale = 1f, hw, hh, baseHw, baseHh;
        final RectF rect = new RectF();
    }

    private final List<Ctl> controls = new ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final float d;

    private Settings st;
    private Sys sys = Sys.NES;
    private boolean port;
    private String ori = "land";
    private float opMul = 1f, sizeMul = 1f;
    private boolean haptic = true;
    private volatile boolean padHidden = false;
    private boolean ffActive = false;
    private boolean pro;

    private Listener listener;
    private EditListener editListener;
    private boolean editMode = false;
    private Ctl selected;
    private float dragDx, dragDy;
    private boolean grid = false;

    /** Oyun is parcaciginin okudugu durum. */
    public volatile int buttons;
    public volatile boolean rewind;
    private int lastMaskForHaptic;

    public PadView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(0x33FFFFFF);
        st = Settings.load(c);
        pro = Pro.has(c);
        setFocusable(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setEditListener(EditListener l) {
        editListener = l;
    }

    void setSystem(Sys s) {
        sys = s;
        buildControls();
        invalidate();
    }

    /** Ayarlar degistiyse yeniden okur. */
    void reload() {
        st = Settings.load(getContext());
        pro = Pro.has(getContext());
        buildControls();
        invalidate();
    }

    void setPadHidden(boolean h) {
        padHidden = h;
        if (h) releaseAll();
        postInvalidate();
    }

    void setFfActive(boolean on) {
        ffActive = on;
        postInvalidate();
    }

    Settings settings() {
        return st;
    }

    // ---- duzenleme arayuzu ----
    void setEditMode(boolean e) {
        editMode = e;
        grid = st.getb("grid", false);
        invalidate();
    }

    boolean gridOn() {
        return grid;
    }

    void setGrid(boolean g) {
        grid = g;
        st.setb("grid", g);
        st.save();
        invalidate();
    }

    String orientationName() {
        return port ? "Dikey" : "Yatay";
    }

    private void notifySel() {
        if (editListener != null)
            editListener.onSelected(selected == null ? null : Actions.ctlName(selected.id),
                    selected != null && selected.visible);
    }

    private void saveCtl(Ctl c) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        st.setPos(sys, ori, c.id, c.cx / w, c.cy / h, c.scale);
        st.setVisible(sys, ori, c.id, c.visible);
        st.save();
    }

    void scaleSelected(float f) {
        if (selected == null) return;
        selected.scale = Math.max(0.5f, Math.min(2.4f, selected.scale * f));
        layoutCtl(selected);
        saveCtl(selected);
        invalidate();
    }

    void toggleSelectedVisible() {
        if (selected == null) return;
        selected.visible = !selected.visible;
        saveCtl(selected);
        notifySel();
        invalidate();
    }

    void resetLayout() {
        st.clearLayout(sys, ori);
        st.save();
        selected = null;
        buildControls();
        notifySel();
        invalidate();
    }

    // ---- varsayilan yerlesim ----
    /** {cx, cy, yaricap} (yuvarlak tuslar icin yaricap, hap tuslar icin 0). */
    private float[] def(String id, int w, int h) {
        boolean snes = sys == Sys.SNES;
        if (port) {
            float top = h - 0.95f * w;
            switch (id) {
                case "dpad": return new float[]{0.24f * w, top + 0.46f * w, 0.17f * w};
                case "b": return snes ? new float[]{0.76f * w, top + 0.58f * w, 0.085f * w}
                        : new float[]{0.68f * w, top + 0.54f * w, 0.10f * w};
                case "a": return snes ? new float[]{0.89f * w, top + 0.45f * w, 0.085f * w}
                        : new float[]{0.88f * w, top + 0.42f * w, 0.10f * w};
                case "y": return new float[]{0.63f * w, top + 0.45f * w, 0.085f * w};
                case "x": return new float[]{0.76f * w, top + 0.32f * w, 0.085f * w};
                case "l": return new float[]{0.17f * w, top + 0.07f * w, 0};
                case "r": return new float[]{0.83f * w, top + 0.07f * w, 0};
                case "select": return new float[]{0.36f * w, top + 0.86f * w, 0};
                case "start": return new float[]{0.64f * w, top + 0.86f * w, 0};
                case "menu": return new float[]{0.50f * w, top + 0.07f * w, 0};
                case "ff": return new float[]{0.50f * w, top + 0.18f * w, 0};
                default: return new float[]{0.50f * w, top + 0.28f * w, 0}; // rew
            }
        }
        switch (id) {
            case "dpad": return new float[]{0.52f * h, 0.66f * h, 0.19f * h};
            case "b": return snes ? new float[]{w - 0.40f * h, 0.81f * h, 0.10f * h}
                    : new float[]{w - 0.62f * h, 0.76f * h, 0.12f * h};
            case "a": return snes ? new float[]{w - 0.25f * h, 0.66f * h, 0.10f * h}
                    : new float[]{w - 0.28f * h, 0.60f * h, 0.12f * h};
            case "y": return new float[]{w - 0.55f * h, 0.66f * h, 0.10f * h};
            case "x": return new float[]{w - 0.40f * h, 0.51f * h, 0.10f * h};
            case "l": return new float[]{0.30f * h, 0.09f * h, 0};
            case "r": return new float[]{w - 0.30f * h, 0.09f * h, 0};
            case "select": return new float[]{w / 2f - 0.20f * h, h - 0.075f * h, 0};
            case "start": return new float[]{w / 2f + 0.20f * h, h - 0.075f * h, 0};
            case "menu": return new float[]{w / 2f, 0.075f * h, 0};
            case "ff": return new float[]{w / 2f + 0.30f * h, 0.075f * h, 0};
            default: return new float[]{w / 2f - 0.30f * h, 0.075f * h, 0}; // rew
        }
    }

    private void layoutCtl(Ctl c) {
        if (c.pill) {
            c.hw = c.baseHw * c.scale * sizeMul;
            c.hh = c.baseHh * c.scale * sizeMul;
            c.rect.set(c.cx - c.hw, c.cy - c.hh, c.cx + c.hw, c.cy + c.hh);
        } else {
            c.r = c.baseR * c.scale * sizeMul;
        }
    }

    private void buildControls() {
        controls.clear();
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        port = h > w;
        ori = port ? "port" : "land";
        opMul = Math.max(10, st.geti("pad.opacity", 55)) / 55f;
        sizeMul = Math.max(50, st.geti("pad.scale", 100)) / 100f;
        haptic = st.getb("pad.haptic", true);

        float pillHw = port ? 0.085f * w : 0.12f * h;
        float pillHh = port ? 0.045f * w : 0.055f * h;

        for (String id : Actions.ctlIds(sys)) {
            Ctl c = new Ctl();
            c.id = id;
            c.label = Actions.ctlLabel(id);
            c.dpad = id.equals("dpad");
            c.pill = Actions.isPill(id);
            c.mask = Actions.mask(id);
            c.locked = (id.equals("ff") || id.equals("rew")) && !pro;
            c.visible = st.visible(sys, ori, id);
            float[] df = def(id, w, h);
            float[] p = st.pos(sys, ori, id);
            c.baseR = df[2];
            c.baseHw = pillHw;
            c.baseHh = pillHh;
            if (p != null) {
                c.cx = p[0] * w;
                c.cy = p[1] * h;
                c.scale = p[2];
            } else {
                c.cx = df[0];
                c.cy = df[1];
            }
            layoutCtl(c);
            controls.add(c);
        }
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

    private void releaseAll() {
        for (Ctl c : controls) {
            c.down = false;
            c.dirs = 0;
        }
        buttons = 0;
        rewind = false;
    }

    // ---- dokunma ----
    private boolean hit(Ctl c, float x, float y) {
        if (c.pill) {
            float m = 8 * d;
            return x >= c.rect.left - m && x <= c.rect.right + m && y >= c.rect.top - m && y <= c.rect.bottom + m;
        }
        float dx = x - c.cx, dy = y - c.cy;
        float rr = c.r * (c.dpad ? 1.22f : 1.1f);
        return dx * dx + dy * dy <= rr * rr;
    }

    private int dirOf(Ctl c, float x, float y) {
        float dx = (x - c.cx) / c.r, dy = (y - c.cy) / c.r;
        int m = 0;
        float t = 0.30f;
        if (dx < -t) m |= L;
        else if (dx > t) m |= R;
        if (dy < -t) m |= U;
        else if (dy > t) m |= D;
        return m;
    }

    private boolean visibleNow(Ctl c) {
        if (!c.visible) return false;
        if (padHidden) return c.id.equals("menu"); // kumanda bagliyken yalnizca menu
        return true;
    }

    /** Tum parmaklardan basili tuslari yeniden hesaplar. skipId: kalkan parmak. */
    private void recompute(MotionEvent e, int skipId) {
        for (Ctl c : controls) {
            c.down = false;
            c.dirs = 0;
        }
        int n = e.getPointerCount();
        for (int i = 0; i < n; i++) {
            if (e.getPointerId(i) == skipId) continue;
            float x = e.getX(i), y = e.getY(i);
            for (Ctl c : controls) {
                if (!visibleNow(c) || !hit(c, x, y)) continue;
                if (c.dpad) c.dirs |= dirOf(c, x, y);
                else c.down = true;
            }
        }
        publish();
    }

    private void publish() {
        int b = 0;
        boolean rew = false;
        for (Ctl c : controls) {
            if (c.dpad) {
                if ((c.dirs & U) != 0) b |= Actions.UP;
                if ((c.dirs & D) != 0) b |= Actions.DOWN;
                if ((c.dirs & L) != 0) b |= Actions.LEFT;
                if ((c.dirs & R) != 0) b |= Actions.RIGHT;
            } else if (c.down) {
                if (c.id.equals("rew")) rew = !c.locked;
                else b |= c.mask;
            }
        }
        // karsit yonler ayni anda olamaz
        if ((b & Actions.UP) != 0 && (b & Actions.DOWN) != 0) b &= ~(Actions.UP | Actions.DOWN);
        if ((b & Actions.LEFT) != 0 && (b & Actions.RIGHT) != 0) b &= ~(Actions.LEFT | Actions.RIGHT);
        if (haptic && (b & ~lastMaskForHaptic) != 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        lastMaskForHaptic = b;
        buttons = b;
        rewind = rew;
    }

    /** Yeni basan parmagin dokundugu menu / hizlandir gibi tek seferlik tuslar. */
    private void tapDown(float x, float y) {
        for (Ctl c : controls) {
            if (!visibleNow(c) || !c.pill || !hit(c, x, y)) continue;
            if (listener == null) return;
            switch (c.id) {
                case "menu":
                    listener.onMenu();
                    return;
                case "ff":
                    if (c.locked) listener.onLocked("Hızlandırma");
                    else listener.onFfToggle();
                    return;
                case "rew":
                    if (c.locked) listener.onLocked("Geri sarma");
                    return;
                default:
            }
        }
    }

    private void editDown(float x, float y) {
        selected = null;
        for (int i = controls.size() - 1; i >= 0; i--) {
            Ctl c = controls.get(i);
            if (!hit(c, x, y)) continue;
            selected = c;
            dragDx = c.cx - x;
            dragDy = c.cy - y;
            break;
        }
        notifySel();
    }

    private void editMove(float x, float y) {
        if (selected == null) return;
        float nx = x + dragDx, ny = y + dragDy;
        if (grid) {
            float g = 24 * d;
            nx = Math.round(nx / g) * g;
            ny = Math.round(ny / g) * g;
        }
        selected.cx = Math.max(0, Math.min(getWidth(), nx));
        selected.cy = Math.max(0, Math.min(getHeight(), ny));
        layoutCtl(selected);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (editMode) {
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                    editDown(e.getX(), e.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    editMove(e.getX(), e.getY());
                    break;
                case MotionEvent.ACTION_UP:
                    if (selected != null) saveCtl(selected);
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }
        int idx = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                tapDown(e.getX(idx), e.getY(idx));
                recompute(e, -1);
                break;
            case MotionEvent.ACTION_MOVE:
                recompute(e, -1);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                recompute(e, e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                lastMaskForHaptic = 0;
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    // ---- cizim ----
    private int al(int base) {
        return Math.min(255, (int) (base * opMul));
    }

    private void drawCtl(Canvas cv, Ctl c, boolean dim, boolean sel) {
        boolean on = !editMode && (c.down || (c.id.equals("ff") && ffActive));
        int fa = on ? al(0x80) : al(0x30);
        int sa = dim ? 0x60 : al(0xA0);
        int ta = dim ? 0x70 : al(0xE0);
        if (dim) fa = 0x14;
        if (c.locked && !editMode) {
            fa = al(0x14);
            sa = al(0x50);
            ta = al(0x70);
        }
        fill.setColor((fa << 24) | 0xFFFFFF);
        stroke.setColor((sa << 24) | (sel ? 0xFFD54F : 0xFFFFFF));
        stroke.setStrokeWidth((sel ? 4f : 2f) * d);
        text.setColor((ta << 24) | 0xFFFFFF);

        if (c.dpad) {
            cv.drawCircle(c.cx, c.cy, c.r, fill);
            cv.drawCircle(c.cx, c.cy, c.r, stroke);
            // arti isareti
            float arm = c.r * 0.84f, th = c.r * 0.30f;
            fill.setColor((al(0x40) << 24) | 0xFFFFFF);
            cv.drawRoundRect(new RectF(c.cx - arm, c.cy - th, c.cx + arm, c.cy + th), th * 0.5f, th * 0.5f, fill);
            cv.drawRoundRect(new RectF(c.cx - th, c.cy - arm, c.cx + th, c.cy + arm), th * 0.5f, th * 0.5f, fill);
            if (!editMode) {
                fill.setColor((al(0xA0) << 24) | 0xFFFFFF);
                float q = c.r * 0.50f;
                if ((c.dirs & U) != 0) cv.drawCircle(c.cx, c.cy - q, th * 0.62f, fill);
                if ((c.dirs & D) != 0) cv.drawCircle(c.cx, c.cy + q, th * 0.62f, fill);
                if ((c.dirs & L) != 0) cv.drawCircle(c.cx - q, c.cy, th * 0.62f, fill);
                if ((c.dirs & R) != 0) cv.drawCircle(c.cx + q, c.cy, th * 0.62f, fill);
            }
            text.setTextSize(c.r * 0.20f);
            stroke.setStrokeWidth(2f * d);
            return;
        }
        if (c.pill) {
            cv.drawRoundRect(c.rect, c.hh, c.hh, fill);
            cv.drawRoundRect(c.rect, c.hh, c.hh, stroke);
            text.setTextSize(Math.min(c.hh * 0.95f, c.hw * 0.48f));
            float ty = c.rect.centerY() - (text.ascent() + text.descent()) / 2f;
            String lb = c.locked && !editMode ? "🔒" : c.label;
            cv.drawText(lb, c.rect.centerX(), ty, text);
        } else {
            cv.drawCircle(c.cx, c.cy, c.r, fill);
            cv.drawCircle(c.cx, c.cy, c.r, stroke);
            text.setTextSize(c.r * 0.82f);
            float ty = c.cy - (text.ascent() + text.descent()) / 2f;
            cv.drawText(c.label, c.cx, ty, text);
        }
        stroke.setStrokeWidth(2f * d);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (editMode) {
            drawEditBackdrop(cv);
            for (Ctl c : controls) drawCtl(cv, c, !c.visible, c == selected);
            return;
        }
        for (Ctl c : controls) {
            if (!visibleNow(c)) continue;
            drawCtl(cv, c, false, false);
        }
    }

    /** Duzenlerken oyun ekraninin nereye gelecegini gosterir ve ızgarayi cizer. */
    private void drawEditBackdrop(Canvas cv) {
        RectF vr = videoRect(getWidth(), getHeight(), sys == Sys.SNES ? 1.333f : 1.2f, 0, 0, "fit", 24 * d);
        fill.setColor(0x33FFFFFF);
        cv.drawRoundRect(vr, 6 * d, 6 * d, fill);
        text.setColor(0x99FFFFFF);
        text.setTextSize(14 * d);
        cv.drawText("OYUN EKRANI", vr.centerX(), vr.centerY(), text);
        if (grid) {
            float g = 24 * d;
            for (float x = 0; x < getWidth(); x += g) cv.drawLine(x, 0, x, getHeight(), gridPaint);
            for (float y = 0; y < getHeight(); y += g) cv.drawLine(0, y, getWidth(), y, gridPaint);
        }
    }

    /** Oyun goruntusunun ekranda kaplayacagi alan (dikeyde ustte, yatayda ortada). */
    static RectF videoRect(int sw, int sh, float asp, int srcW, int srcH, String mode, float topInset) {
        boolean portrait = sh > sw;
        float availW = sw, availH = sh, t0 = 0;
        if (portrait) {
            float ctlTop = sh - 0.95f * sw;
            t0 = topInset + sw * 0.01f;
            availH = Math.max(sh * 0.30f, ctlTop - t0);
        }
        float dw, dh;
        if ("stretch".equals(mode)) {
            dw = availW;
            dh = availH;
        } else {
            dw = availW;
            dh = dw / asp;
            if (dh > availH) {
                dh = availH;
                dw = dh * asp;
            }
            if ("integer".equals(mode) && srcH > 0) {
                float k = (float) Math.floor(Math.min(availW / (srcH * asp), availH / srcH));
                if (k >= 1f) {
                    dh = k * srcH;
                    dw = dh * asp;
                }
            }
        }
        float l = (sw - dw) / 2f;
        float t = portrait ? t0 + Math.max(0f, (availH - dh) / 2f) : (sh - dh) / 2f;
        return new RectF(l, t, l + dw, t + dh);
    }
}
