package com.retrooyun.baslatici;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekran uzerindeki dokunmatik kontroller. Konum, boyut, gorunurluk ve hassasiyet
 * Ayarlar'dan gelir. Duzenleme modunda tuslar surukleyerek yerlestirilir.
 */
public class TouchOverlay extends View {
    interface EditListener {
        void onSelected(String name, boolean visible);
    }

    private static final int MODE_GAME = 0, MODE_MENU = 1;
    private static final int ACT_NONE = 0, ACT_OPEN_MENU = 1, ACT_CLOSE_MENU = 2;

    private static final class Ctl {
        String id, label, action;
        int mask, mode, act;
        boolean round, pressed, visible, editable;
        float cx, cy, r, baseR, scale = 1f;
        final RectF rect = new RectF();
    }

    private final List<Ctl> controls = new ArrayList<>();
    private final SparseArray<Ctl> pointerCtl = new SparseArray<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final float d;

    private Settings st;
    private String kind = Games.KIND_DOOM;
    private boolean twin = false;
    private boolean fixedStick = false;
    private float moveSens = 1f, turnSens = 1f, opMul = 1f;
    private boolean invert = false;
    private String[] dtAct = {"none", "none"};
    private int mode = MODE_GAME;
    private volatile boolean padHidden = false;

    private int stickId = -1;
    private float sx0, sy0, sx, sy;
    private int lookId = -1;
    private float lkx0, lky0, lkx, lky;

    // cift dokunus
    private final long[] lastDown = {0, 0};
    private final float[] lastX = {0, 0}, lastY = {0, 0};
    private final int[] dtPtr = {-1, -1};

    // duzenleme
    private boolean editMode = false;
    private Ctl selected;
    private float dragDx, dragDy;
    private boolean grid = false;
    private EditListener editListener;

    /** Oyun is parcaciginin okudugu durum. */
    public volatile int buttons;
    public volatile int lx, ly, rx, ry;

    public TouchOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(0x33FFFFFF);
        st = Settings.load(c);
        setFocusable(false);
    }

    public void setKind(String k) {
        kind = k;
        buildControls();
        invalidate();
    }

    /** Ayarlar degistiyse yeniden okur. */
    public void reload() {
        st = Settings.load(getContext());
        buildControls();
        invalidate();
    }

    Settings settings() {
        return st;
    }

    public void setPadHidden(boolean h) {
        padHidden = h;
        if (h) releaseAll();
        postInvalidate();
    }

    public void toggleMenu() {
        mode = mode == MODE_GAME ? MODE_MENU : MODE_GAME;
        postInvalidate();
    }

    // ---- duzenleme arayuzu ----
    void setEditMode(boolean e) {
        editMode = e;
        grid = st.getb("grid", false);
        invalidate();
    }

    void setEditListener(EditListener l) {
        editListener = l;
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

    private void notifySel() {
        if (editListener != null)
            editListener.onSelected(selected == null ? null : Actions.ctlName(kind, selected.id),
                    selected != null && selected.visible);
    }

    private void saveCtl(Ctl c) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        st.setPos(kind, c.id, c.cx / w, c.cy / h, c.scale);
        st.setVisible(kind, c.id, c.visible);
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
        st.clearLayout(kind);
        st.save();
        selected = null;
        buildControls();
        notifySel();
        invalidate();
    }

    void applyPreset(String name) {
        st.applyPreset(kind, name);
        st.save();
        selected = null;
        buildControls();
        notifySel();
        invalidate();
    }

    void reloadLayoutFromSettings() {
        selected = null;
        buildControls();
        notifySel();
        invalidate();
    }

    // ---- kurulum ----
    private float[] def(String id, int w, int h) {
        float cx = w / 2f;
        switch (id) {
            case "stick": return new float[]{120 * d, h - 110 * d, 70 * d};
            case "fire": return new float[]{w - 95 * d, h - 100 * d, 54 * d};
            case "use": return new float[]{w - 200 * d, h - 62 * d, 38 * d};
            case "run": return new float[]{w - 62 * d, h - 205 * d, 36 * d};
            case "sl": return new float[]{w - 290 * d, h - 62 * d, 32 * d};
            case "sr": return new float[]{w - 215 * d, h - 150 * d, 32 * d};
            case "fire2": return new float[]{w - 62 * d, 62 * d, 44 * d};
            case "prev": return new float[]{cx - 138 * d, 30 * d, 0};
            case "menu": return new float[]{cx - 46 * d, 30 * d, 0};
            case "next": return new float[]{cx + 46 * d, 30 * d, 0};
            default: return new float[]{cx + 138 * d, 30 * d, 0}; // map
        }
    }

    private void layoutCtl(Ctl c) {
        if (c.round) {
            c.r = c.baseR * c.scale;
        } else {
            float hw = 42 * d * c.scale, hh = 20 * d * c.scale;
            c.rect.set(c.cx - hw, c.cy - hh, c.cx + hw, c.cy + hh);
        }
    }

    private Ctl addMenu(boolean round, String label, int mask, int act, float cx, float cy, float r) {
        Ctl c = new Ctl();
        c.id = "m_" + label;
        c.round = round;
        c.label = label;
        c.mask = mask;
        c.mode = MODE_MENU;
        c.act = act;
        c.cx = cx;
        c.cy = cy;
        c.baseR = r;
        c.visible = true;
        c.editable = false;
        layoutCtl(c);
        controls.add(c);
        return c;
    }

    private void buildControls() {
        controls.clear();
        pointerCtl.clear();
        stickId = lookId = -1;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        twin = st.twin(kind);
        fixedStick = st.getb("fixed", false);
        moveSens = Math.max(0.3f, st.geti("sens.move", 100) / 100f);
        turnSens = Math.max(0.3f, st.geti("sens.turn", 100) / 100f);
        opMul = Math.max(10, st.geti("opacity", 60)) / 60f;
        invert = st.getb("invert", false);
        dtAct[0] = st.gets("dt.left", "none");
        dtAct[1] = st.gets("dt.right", "none");

        for (String id : Actions.ctlIds(kind)) {
            Ctl c = new Ctl();
            c.id = id;
            c.label = Actions.ctlLabel(kind, id);
            c.action = Actions.ctlAction(id);
            c.mask = Actions.mask(kind, c.action);
            c.mode = MODE_GAME;
            c.act = id.equals("menu") ? ACT_OPEN_MENU : ACT_NONE;
            c.round = !Actions.isPill(id);
            c.editable = true;
            c.visible = st.visible(kind, id);
            float[] df = def(id, w, h);
            float[] p = st.pos(kind, id);
            c.baseR = df[2];
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

        float cx = w / 2f, ty = 30 * d;
        float mx = 150 * d, my = h - 130 * d;
        addMenu(true, "▲", Actions.UP, ACT_NONE, mx, my - 62 * d, 32 * d);
        addMenu(true, "▼", Actions.DOWN, ACT_NONE, mx, my + 62 * d, 32 * d);
        addMenu(true, "◀", Actions.LEFT, ACT_NONE, mx - 62 * d, my, 32 * d);
        addMenu(true, "▶", Actions.RIGHT, ACT_NONE, mx + 62 * d, my, 32 * d);
        addMenu(true, "TAMAM", Actions.A, ACT_NONE, w - 95 * d, h - 110 * d, 50 * d);
        addMenu(true, "GERİ", Actions.B, ACT_NONE, w - 205 * d, h - 70 * d, 38 * d);
        addMenu(false, "KAPAT", Actions.START, ACT_CLOSE_MENU, cx - 46 * d, ty, 0);
        addMenu(false, "OYUN", 0, ACT_CLOSE_MENU, cx + 46 * d, ty, 0);
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

    private Ctl byId(String id) {
        for (Ctl c : controls) if (c.id.equals(id)) return c;
        return null;
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
        dtPtr[0] = dtPtr[1] = -1;
        publish();
    }

    // ---- dokunma ----
    private void down(int id, float x, float y) {
        for (int i = controls.size() - 1; i >= 0; i--) {
            Ctl c = controls.get(i);
            if (c.mode != mode || !c.visible || c.id.equals("stick") || !hit(c, x, y)) continue;
            c.pressed = true;
            pointerCtl.put(id, c);
            if (c.act == ACT_OPEN_MENU) mode = MODE_MENU;
            else if (c.act == ACT_CLOSE_MENU) mode = MODE_GAME;
            return;
        }
        if (mode != MODE_GAME) return;
        boolean left = x < getWidth() * 0.45f;
        if (left) {
            if (stickId < 0) {
                Ctl sc = byId("stick");
                if (fixedStick && sc != null) {
                    float dx = x - sc.cx, dy = y - sc.cy, rr = sc.r * 1.6f;
                    if (dx * dx + dy * dy <= rr * rr) {
                        stickId = id;
                        sx0 = sc.cx;
                        sy0 = sc.cy;
                        sx = x;
                        sy = y;
                    }
                } else {
                    stickId = id;
                    sx0 = sx = x;
                    sy0 = sy = y;
                }
            }
        } else if (twin && lookId < 0) {
            lookId = id;
            lkx0 = lkx = x;
            lky0 = lky = y;
        }
        // cift dokunus: ikinci dokunus 350 ms icinde ve yakinsa, parmak basili kaldigi surece islev basili
        int side = left ? 0 : 1;
        if (!dtAct[side].equals("none")) {
            long now = SystemClock.uptimeMillis();
            float dx = x - lastX[side], dy = y - lastY[side];
            if (now - lastDown[side] < 350 && dx * dx + dy * dy < 90 * d * 90 * d) {
                dtPtr[side] = id;
                lastDown[side] = 0;
            } else {
                lastDown[side] = now;
                lastX[side] = x;
                lastY[side] = y;
            }
        }
    }

    private void move(int id, float x, float y) {
        if (id == stickId) {
            sx = x;
            sy = y;
        } else if (id == lookId) {
            lkx = x;
            lky = y;
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
        if (dtPtr[0] == id) dtPtr[0] = -1;
        if (dtPtr[1] == id) dtPtr[1] = -1;
    }

    // ---- duzenleme dokunmalari ----
    private void editDown(float x, float y) {
        selected = null;
        for (int i = controls.size() - 1; i >= 0; i--) {
            Ctl c = controls.get(i);
            if (!c.editable || !hit(c, x, y)) continue;
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
        int idx = e.getActionIndex();
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
        if (padHidden) return false;
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

    private static int clampI(float v) {
        return (int) Math.max(-32767f, Math.min(32767f, v));
    }

    private float stickRadius() {
        Ctl sc = byId("stick");
        float base = sc != null ? sc.r : 70 * d;
        return base / moveSens;
    }

    private void publish() {
        int b = 0, strafe = 0;
        for (Ctl c : controls) {
            if (!c.pressed) continue;
            b |= c.mask;
            if ("sl".equals(c.action)) strafe -= 32767;
            else if ("sr".equals(c.action)) strafe += 32767;
        }
        for (int s = 0; s < 2; s++) {
            if (dtPtr[s] < 0) continue;
            b |= Actions.mask(kind, dtAct[s]);
        }
        buttons = b;

        int nlx = 0, nly = 0;
        if (stickId >= 0) {
            float R = stickRadius();
            float dx = (sx - sx0) / R, dy = (sy - sy0) / R;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m > 1f) {
                dx /= m;
                dy /= m;
            }
            nlx = (int) (dx * 32767);
            nly = (int) (dy * 32767);
        }
        if (!twin) {
            // Tek cubuk: yukari/asagi = ileri/geri, saga/sola = DON. Sag el serbest kalir.
            float t = nlx / 32767f;
            rx = clampI(Math.signum(t) * Math.pow(Math.abs(t), 1.3) * 32767 * turnSens);
            ry = 0;
            lx = clampI(strafe);
            ly = nly;
            return;
        }
        lx = clampI(nlx + strafe);
        ly = nly;
        if (lookId >= 0) {
            float R = 55 * d;
            float dx = (lkx - lkx0) / R, dy = (lky - lky0) / R;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m > 1f) {
                dx /= m;
                dy /= m;
                m = 1f;
            }
            float k = m > 0f ? (float) Math.pow(m, 1.3) / m : 0f;
            rx = clampI(dx * k * 32767 * turnSens);
            ry = clampI(dy * k * 32767 * turnSens * (invert ? -1f : 1f));
        } else {
            rx = 0;
            ry = 0;
        }
    }

    // ---- cizim ----
    private int al(int base) {
        return Math.min(255, (int) (base * opMul));
    }

    private void drawCtl(Canvas cv, Ctl c, boolean dim, boolean sel) {
        int fa = c.pressed ? al(0x78) : al(0x30);
        if (dim) fa = 0x14;
        fill.setColor((fa << 24) | 0xFFFFFF);
        stroke.setColor((dim ? 0x60 : al(0xA0)) << 24 | (sel ? 0xFFD54F : 0xFFFFFF));
        stroke.setStrokeWidth((sel ? 4f : 2f) * d);
        text.setColor((dim ? 0x70 : al(0xDD)) << 24 | 0xFFFFFF);
        if (c.round) {
            cv.drawCircle(c.cx, c.cy, c.r, fill);
            cv.drawCircle(c.cx, c.cy, c.r, stroke);
            text.setTextSize(Math.min(c.r * 0.42f, 20 * d));
            float ty = c.cy - (text.ascent() + text.descent()) / 2f;
            cv.drawText(c.label, c.cx, ty, text);
        } else {
            cv.drawRoundRect(c.rect, 14 * d, 14 * d, fill);
            cv.drawRoundRect(c.rect, 14 * d, 14 * d, stroke);
            text.setTextSize(13 * d * Math.min(1.3f, c.scale));
            float ty = c.rect.centerY() - (text.ascent() + text.descent()) / 2f;
            cv.drawText(c.label, c.rect.centerX(), ty, text);
        }
        stroke.setStrokeWidth(2f * d);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (editMode) {
            if (grid) {
                float g = 24 * d;
                for (float x = 0; x < getWidth(); x += g) cv.drawLine(x, 0, x, getHeight(), gridPaint);
                for (float y = 0; y < getHeight(); y += g) cv.drawLine(0, y, getWidth(), y, gridPaint);
            }
            for (Ctl c : controls) {
                if (c.mode != MODE_GAME) continue;
                boolean dim = !c.visible;
                if (c.id.equals("stick")) {
                    fill.setColor(0x30FFFFFF);
                    stroke.setColor((c == selected ? 0xFFFFD54F : 0xB0FFFFFF));
                    stroke.setStrokeWidth((c == selected ? 4f : 2f) * d);
                    cv.drawCircle(c.cx, c.cy, c.r, fill);
                    cv.drawCircle(c.cx, c.cy, c.r, stroke);
                    stroke.setStrokeWidth(2f * d);
                    text.setColor(0xDDFFFFFF);
                    text.setTextSize(13 * d);
                    cv.drawText(dim ? "ÇUBUK (gizli)" : "ÇUBUK", c.cx, c.cy + 4 * d, text);
                } else {
                    drawCtl(cv, c, dim, c == selected);
                }
            }
            return;
        }
        if (padHidden) return;

        for (Ctl c : controls) {
            if (c.mode != mode || !c.visible || c.id.equals("stick")) continue;
            drawCtl(cv, c, false, false);
        }
        if (mode != MODE_GAME) return;

        Ctl sc = byId("stick");
        float R = sc != null ? sc.r : 70 * d;
        stroke.setColor(al(0xA0) << 24 | 0xFFFFFF);
        if (stickId >= 0) {
            float dx = sx - sx0, dy = sy - sy0;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            float lim = stickRadius();
            if (m > lim) {
                dx = dx / m * lim;
                dy = dy / m * lim;
            }
            fill.setColor(al(0x24) << 24 | 0xFFFFFF);
            cv.drawCircle(sx0, sy0, R, fill);
            cv.drawCircle(sx0, sy0, R, stroke);
            fill.setColor(al(0x80) << 24 | 0xFFFFFF);
            cv.drawCircle(sx0 + dx * R / lim, sy0 + dy * R / lim, 28 * d * (sc != null ? sc.scale : 1f), fill);
        } else if (sc != null && sc.visible) {
            stroke.setColor(al(0x40) << 24 | 0xFFFFFF);
            cv.drawCircle(sc.cx, sc.cy, R, stroke);
        }
        float RL = 55 * d;
        if (twin && sc != null && sc.visible) {
            if (lookId >= 0) {
                float dx = lkx - lkx0, dy = lky - lky0;
                float m = (float) Math.sqrt(dx * dx + dy * dy);
                if (m > RL) {
                    dx = dx / m * RL;
                    dy = dy / m * RL;
                }
                stroke.setColor(al(0xA0) << 24 | 0xFFFFFF);
                fill.setColor(al(0x24) << 24 | 0xFFFFFF);
                cv.drawCircle(lkx0, lky0, RL, fill);
                cv.drawCircle(lkx0, lky0, RL, stroke);
                fill.setColor(al(0x80) << 24 | 0xFFFFFF);
                cv.drawCircle(lkx0 + dx, lky0 + dy, 24 * d, fill);
            } else {
                stroke.setColor(al(0x40) << 24 | 0xFFFFFF);
                cv.drawCircle(getWidth() * 0.62f, getHeight() * 0.5f, RL, stroke);
                text.setTextSize(11 * d);
                text.setColor(al(0x50) << 24 | 0xFFFFFF);
                cv.drawText("DÖN", getWidth() * 0.62f, getHeight() * 0.5f + 4 * d, text);
            }
        }
    }
}
