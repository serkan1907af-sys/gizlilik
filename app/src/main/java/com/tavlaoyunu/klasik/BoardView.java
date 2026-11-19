package com.tavlaoyunu.klasik;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.tavlaoyunu.klasik.engine.Board;
import com.tavlaoyunu.klasik.engine.Game;
import com.tavlaoyunu.klasik.engine.Move;

import java.util.List;
import java.util.Random;

/**
 * Tahtayı çizer ve dokunmaları işler.
 * Ekran düzeni (insan = beyaz, 1..24 kendi numaralaması):
 *   alt sıra soldan sağa: 12..7 | bar | 6..1   (kendi evin sağ altta)
 *   üst sıra soldan sağa: 13..18 | bar | 19..24
 * Bilgisayarın j noktası ekranda (25 - j) noktasında görünür.
 */
public class BoardView extends View {
    public interface Listener {
        void onRollRequested();

        void onMoveChosen(Move m);
    }

    private Game game;
    private BoardTheme theme = BoardTheme.get(0);
    private Listener listener;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    // geometri
    private float bl, bt, bw, bh;      // dış çerçeve
    private float il, it, iw, ih;      // iç alan
    private float colW, trayW, triH, r, midBand, midY, step;
    private int maxShow = 5;

    // seçim / ipucu
    private int selectedFrom = -1;
    private Move hintMove;

    // zar animasyonu
    private boolean rolling = false;
    private int rollFaceA = 1, rollFaceB = 1;
    private float rollWobble = 0f;

    // hamle animasyonu
    private boolean moving = false;
    private int movPlayer;
    private float mx0, my0, mx1, my1, mProg;
    private int hideScreenPoint = -1;
    private int hidePlayer = -1;
    private boolean hideTray = false;
    private boolean locked = false;

    // sürükle-bırak
    private int dragFrom = -1;
    private boolean dragging = false;
    private float dragX, dragY, downX, downY;

    // önbellekler
    private Bitmap boardBmp;
    private final Bitmap[] chkBmp = new Bitmap[2];
    private int chkSize;
    private float rollT = 0f;

    // zar fiziği
    private final float[] dX = new float[2], dY = new float[2], dVx = new float[2], dVy = new float[2];
    private final float[] dAng = new float[2], dAngV = new float[2], dTilt = new float[2], dSpin = new float[2];
    private final int[] dFace = {6, 3};
    private boolean diceLanded = false;
    private boolean holding = false;
    private float holdX, holdY, holdVx, holdVy;
    private long holdT;
    private boolean pendingThrow = false;
    private float throwX, throwY, throwVx, throwVy;
    private boolean throwHasVel = false;
    private long lastBounceSfx = 0;
    private float diceS = 0f;
    private Economy.Item diceSkin = Economy.find("d0");
    private Economy.Item checkerSkin = Economy.find("c0");

    private float pulse = 0f;
    private ValueAnimator pulseAnim;

    public BoardView(Context c) {
        super(c);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setFakeBoldText(true);
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(1100);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pulse = (Float) a.getAnimatedValue();
                if (game != null && game.turn == Game.HUMAN && !game.over) invalidate();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulseAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pulseAnim.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    public void setGame(Game g) {
        game = g;
        selectedFrom = -1;
        hintMove = null;
        moving = false;
        rolling = false;
        diceLanded = false;
        holding = false;
        invalidate();
    }

    public void setTheme(BoardTheme t) {
        theme = t;
        buildAssets();
        invalidate();
    }

    public void setSkins(Economy.Item dice, Economy.Item checker) {
        diceSkin = dice;
        checkerSkin = checker;
        buildAssets();
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setLocked(boolean v) {
        locked = v;
    }

    public void clearSelection() {
        selectedFrom = -1;
        hintMove = null;
        invalidate();
    }

    public void showHint(Move m) {
        hintMove = m;
        selectedFrom = -1;
        invalidate();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hintMove = null;
                invalidate();
            }
        }, 3500);
    }

    // ------------------------------------------------------------ geometri

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float margin = w * 0.012f;
        float availW = w - 2 * margin;
        float availH = h - 2 * margin;
        bw = availW;
        bh = Math.min(availH, bw * 1.65f);
        bl = margin;
        bt = margin + (availH - bh) / 2f;
        float f = bw * 0.018f;
        il = bl + f;
        it = bt + f;
        iw = bw - 2 * f;
        ih = bh - 2 * f;
        colW = iw / 13.9f;
        trayW = colW * 0.9f;
        triH = ih * 0.435f;
        midBand = ih - 2 * triH;
        midY = it + ih / 2f;
        r = Math.min(colW * 0.46f, triH / 9f);
        step = 2 * r * 0.9f;
        maxShow = Math.max(5, Math.min(10, (int) ((triH - 2 * r - 6) / step) + 1));
        buildAssets();
    }

    private static int shade(int color, float f) {
        int a = color >>> 24;
        int rr = Math.min(255, Math.max(0, (int) (((color >> 16) & 255) * f)));
        int gg = Math.min(255, Math.max(0, (int) (((color >> 8) & 255) * f)));
        int bb = Math.min(255, Math.max(0, (int) ((color & 255) * f)));
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Tahta ve taş görsellerini bir kez çizip önbelleğe alır. */
    private void buildAssets() {
        if (bw <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        // --- tahta
        boardBmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(boardBmp);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(theme.bg);
        // dış gölge
        q.setColor(0x88000000);
        q.setMaskFilter(new BlurMaskFilter(bw * 0.02f, BlurMaskFilter.Blur.NORMAL));
        rect.set(bl + 2, bt + bw * 0.01f, bl + bw + 2, bt + bh + bw * 0.01f);
        c.drawRoundRect(rect, colW * 0.35f, colW * 0.35f, q);
        q.setMaskFilter(null);
        // çerçeve (ahşap geçişi)
        q.setShader(new LinearGradient(bl, bt, bl + bw, bt + bh, shade(theme.frame, 1.35f), shade(theme.frame, 0.7f), Shader.TileMode.CLAMP));
        rect.set(bl, bt, bl + bw, bt + bh);
        c.drawRoundRect(rect, colW * 0.35f, colW * 0.35f, q);
        q.setShader(null);
        // çerçeve damarları
        Random gr = new Random(7);
        q.setStrokeWidth(1.2f);
        for (int i = 0; i < 160; i++) {
            float y = bt + gr.nextFloat() * bh;
            float x1 = bl + gr.nextFloat() * bw;
            q.setColor(gr.nextBoolean() ? 0x14000000 : 0x10FFFFFF);
            c.drawLine(x1, y, Math.min(bl + bw, x1 + bw * (0.1f + gr.nextFloat() * 0.4f)), y + gr.nextFloat() * 3f, q);
        }
        // oyun alanı (içe oyuk)
        q.setShader(new LinearGradient(il, it, il, it + ih, shade(theme.board, 1.12f), shade(theme.board, 0.85f), Shader.TileMode.CLAMP));
        rect.set(il, it, il + iw, it + ih);
        c.drawRoundRect(rect, colW * 0.2f, colW * 0.2f, q);
        q.setShader(null);
        for (int i = 0; i < 220; i++) {
            float y = it + gr.nextFloat() * ih;
            float x1 = il + gr.nextFloat() * iw;
            q.setColor(gr.nextBoolean() ? 0x0F000000 : 0x0CFFFFFF);
            c.drawLine(x1, y, Math.min(il + iw, x1 + iw * (0.1f + gr.nextFloat() * 0.5f)), y + gr.nextFloat() * 2f, q);
        }
        // bar ve tepsi
        q.setShader(new LinearGradient(il + 6 * colW, 0, il + 7 * colW, 0, shade(theme.bar, 1.25f), shade(theme.bar, 0.8f), Shader.TileMode.CLAMP));
        rect.set(il + 6 * colW, it, il + 7 * colW, it + ih);
        c.drawRect(rect, q);
        q.setShader(new LinearGradient(trayLeft(), 0, il + iw, 0, shade(theme.bar, 0.8f), shade(theme.bar, 1.2f), Shader.TileMode.CLAMP));
        rect.set(trayLeft(), it, il + iw, it + ih);
        c.drawRect(rect, q);
        q.setShader(null);
        // üçgenler
        for (int n = 1; n <= 24; n++) {
            boolean bottom = n <= 12;
            float x = colX(colOf(n));
            float baseY = bottom ? it + ih : it;
            float apexY = bottom ? it + ih - triH : it + triH;
            int base = (n % 2 == 1) ? theme.pointA : theme.pointB;
            q.setShader(new LinearGradient(x, baseY, x, apexY, shade(base, 0.8f), shade(base, 1.18f), Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(x - colW / 2f + 1.5f, baseY);
            path.lineTo(x + colW / 2f - 1.5f, baseY);
            path.lineTo(x, apexY);
            path.close();
            c.drawPath(path, q);
        }
        q.setShader(null);
        // iç kenar çizgisi
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(Math.max(2f, colW * 0.06f));
        q.setColor(0x66000000);
        rect.set(il, it, il + iw, it + ih);
        c.drawRoundRect(rect, colW * 0.2f, colW * 0.2f, q);

        // --- taşlar
        chkSize = (int) Math.ceil(r * 2.6f);
        for (int pl = 0; pl < 2; pl++) {
            chkBmp[pl] = Skins.makeChecker(pl, r, chkSize, theme, checkerSkin);
        }
    }

    private int colOf(int n) {
        if (n <= 12) return n >= 7 ? 12 - n : 13 - n;
        return n <= 18 ? n - 13 : n - 12;
    }

    private float colX(int col) {
        return il + (col + 0.5f) * colW;
    }

    private float stackY(boolean bottom, int slot) {
        if (slot >= maxShow) slot = maxShow - 1;
        if (bottom) return it + ih - 4 - r - slot * step;
        return it + 4 + r + slot * step;
    }

    private float barX() {
        return colX(6);
    }

    private float barY(int player, int slot) {
        if (slot > 3) slot = 3;
        float gap = r * 1.15f;
        // insan alt yarıda, bilgisayar üst yarıda
        if (player == Game.HUMAN) return midY + gap + slot * step;
        return midY - gap - slot * step;
    }

    private float trayX() {
        return il + 13 * colW + trayW / 2f + (iw - 13 * colW - trayW) * 0.5f;
    }

    private float trayLeft() {
        return il + 13 * colW;
    }

    private int screenPointOf(int player, int ownPoint) {
        return player == Game.HUMAN ? ownPoint : 25 - ownPoint;
    }

    // ------------------------------------------------------------ çizim

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (game == null || bw <= 0) return;
        Board b = game.board;

        if (boardBmp == null) buildAssets();
        if (boardBmp != null) c.drawBitmap(boardBmp, 0, 0, null);
        else c.drawColor(theme.bg);

        // hamle göstergeleri (kaynak halkaları, hedef noktalar)
        drawGuides(c);

        // taşlar
        for (int n = 1; n <= 24; n++) {
            int white = b.pts[Game.HUMAN][n];
            int black = b.pts[Game.CPU][25 - n];
            if (white > 0) {
                int cnt = (hidePlayer == Game.HUMAN && hideScreenPoint == n) ? white - 1 : white;
                if (dragging && dragFrom == n) cnt--;
                drawStack(c, n, Game.HUMAN, cnt);
            } else if (black > 0) {
                int cnt = (hidePlayer == Game.CPU && hideScreenPoint == n) ? black - 1 : black;
                drawStack(c, n, Game.CPU, cnt);
            }
        }
        // bar taşları
        int hb = b.bar[Game.HUMAN] - ((dragging && dragFrom == 25) ? 1 : 0);
        for (int k = 0; k < hb; k++) drawChecker(c, barX(), barY(Game.HUMAN, k), Game.HUMAN);
        for (int k = 0; k < b.bar[Game.CPU]; k++) drawChecker(c, barX(), barY(Game.CPU, k), Game.CPU);
        if (b.bar[Game.HUMAN] > 4) drawCount(c, barX(), barY(Game.HUMAN, 3), b.bar[Game.HUMAN]);
        if (b.bar[Game.CPU] > 4) drawCount(c, barX(), barY(Game.CPU, 3), b.bar[Game.CPU]);

        // toplanan taşlar
        drawTray(c, Game.HUMAN, b.off[Game.HUMAN] - ((hideTray && movPlayer == Game.HUMAN) ? 1 : 0));
        drawTray(c, Game.CPU, b.off[Game.CPU] - ((hideTray && movPlayer == Game.CPU) ? 1 : 0));

        // hareket eden taş
        if (moving) {
            float x = mx0 + (mx1 - mx0) * mProg;
            float y = my0 + (my1 - my0) * mProg - (float) Math.sin(mProg * Math.PI) * r * 0.9f;
            drawChecker(c, x, y, movPlayer);
        }

        // sürüklenen taş
        if (dragging) drawChecker(c, dragX, dragY - r * 1.1f, Game.HUMAN);

        // zarlar
        drawDiceArea(c);
    }

    private void drawStack(Canvas c, int n, int player, int count) {
        boolean bottom = n <= 12;
        float x = colX(colOf(n));
        int show = Math.min(count, maxShow);
        for (int k = 0; k < show; k++) drawChecker(c, x, stackY(bottom, k), player);
        if (count > maxShow) drawCount(c, x, stackY(bottom, maxShow - 1), count);
    }

    private void drawCount(Canvas c, float x, float y, int n) {
        tp.setColor(0xFFFFC107);
        tp.setTextSize(r * 1.1f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC000000);
        c.drawCircle(x, y, r * 0.7f, p);
        c.drawText(String.valueOf(n), x, y + r * 0.4f, tp);
    }

    private void drawChecker(Canvas c, float x, float y, int player) {
        Bitmap bm = chkBmp[player];
        if (bm == null) return;
        c.drawBitmap(bm, x - chkSize / 2f, y - chkSize / 2f, null);
    }

    private void drawTray(Canvas c, int player, int count) {
        if (count <= 0) return;
        float w = trayW * 0.8f;
        float h = Math.max(4f, r * 0.42f);
        float cx = trayLeft() + (il + iw - trayLeft()) / 2f;
        for (int k = 0; k < count; k++) {
            float y = player == Game.HUMAN ? (it + ih - 6 - h - k * (h + 2)) : (it + 6 + k * (h + 2));
            p.setStyle(Paint.Style.FILL);
            p.setColor(player == Game.HUMAN ? theme.whiteEdge : theme.blackEdge);
            rect.set(cx - w / 2f, y, cx + w / 2f, y + h);
            c.drawRoundRect(rect, h * 0.3f, h * 0.3f, p);
            p.setColor(player == Game.HUMAN ? theme.white : theme.black);
            rect.set(cx - w / 2f + 1, y + 1, cx + w / 2f - 1, y + h - 1);
            c.drawRoundRect(rect, h * 0.3f, h * 0.3f, p);
        }
    }

    private void drawGuides(Canvas c) {
        if (game.turn != Game.HUMAN || !game.rolled || game.over || moving || rolling) return;
        List<Move> moves = game.nextMoves();
        // oynayabilecek kaynaklar
        if (selectedFrom == -1) {
            for (Move m : moves) {
                float[] xy = sourceTop(m.from);
                if (xy == null) continue;
                drawRing(c, xy[0], xy[1], r * (1.12f + 0.14f * pulse), theme.accent, 0.9f);
            }
        } else {
            float[] xy = sourceTop(selectedFrom);
            if (xy != null) drawRing(c, xy[0], xy[1], r * 1.2f, theme.accent, 1f);
            for (Move m : moves) {
                if (m.from != selectedFrom) continue;
                p.setStyle(Paint.Style.FILL);
                p.setColor(withAlpha(theme.accent, 70 + (int) (50 * pulse)));
                if (m.to >= 1) {
                    triPath(m.to);
                    c.drawPath(path, p);
                } else {
                    rect.set(trayLeft(), it + ih - triH, il + iw, it + ih);
                    c.drawRect(rect, p);
                }
                float[] t = targetSpot(m);
                p.setColor(withAlpha(theme.accent, 190 + (int) (50 * pulse)));
                c.drawCircle(t[0], t[1], r * 0.5f, p);
            }
        }
        if (hintMove != null) {
            float[] a = sourceTop(hintMove.from);
            float[] t = targetSpot(hintMove);
            if (a != null) drawRing(c, a[0], a[1], r * 1.25f, 0xFF4CAF50, 1f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC4CAF50);
            c.drawCircle(t[0], t[1], r * 0.7f, p);
        }
    }

    private void triPath(int n) {
        boolean bottom = n <= 12;
        float x = colX(colOf(n));
        float baseY = bottom ? it + ih : it;
        float apexY = bottom ? it + ih - triH : it + triH;
        path.reset();
        path.moveTo(x - colW / 2f + 1.5f, baseY);
        path.lineTo(x + colW / 2f - 1.5f, baseY);
        path.lineTo(x, apexY);
        path.close();
    }

    private void drawRing(Canvas c, float x, float y, float rad, int color, float alpha) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, r * 0.18f));
        p.setColor(withAlpha(color, (int) (255 * alpha)));
        c.drawCircle(x, y, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static int withAlpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
    }

    /** İnsanın 'from' noktasındaki en üst taşın ekran konumu. */
    private float[] sourceTop(int from) {
        Board b = game.board;
        if (from == 25) {
            int cnt = b.bar[Game.HUMAN];
            if (cnt <= 0) return null;
            return new float[]{barX(), barY(Game.HUMAN, cnt - 1)};
        }
        int cnt = b.pts[Game.HUMAN][from];
        if (cnt <= 0) return null;
        boolean bottom = from <= 12;
        return new float[]{colX(colOf(from)), stackY(bottom, cnt - 1)};
    }

    /** Hedef noktada yeni taşın duracağı yer. */
    private float[] targetSpot(Move m) {
        if (m.to <= 0) {
            return new float[]{trayLeft() + (il + iw - trayLeft()) / 2f, it + ih - r * 1.6f};
        }
        int cnt = game.board.pts[Game.HUMAN][m.to];
        boolean bottom = m.to <= 12;
        int slot = Math.max(0, cnt);
        return new float[]{colX(colOf(m.to)), stackY(bottom, slot)};
    }

    // ------------------------------------------------------------ zarlar

    private float diceSize() {
        return colW * 2.3f;
    }

    private void drawDiceArea(Canvas c) {
        float s = diceSize();
        boolean humanSide = game.turn == Game.HUMAN;
        float cx = humanSide ? il + 10f * colW : il + 3f * colW;
        float cy = midY;

        if (holding) {
            long now = System.currentTimeMillis();
            int f1 = (int) ((now / 90) % 6) + 1, f2 = (int) ((now / 110 + 3) % 6) + 1;
            float j = colW * 0.06f;
            drawDie(c, holdX - s * 0.6f + (rnd.nextFloat() - 0.5f) * j * 2, holdY + (rnd.nextFloat() - 0.5f) * j * 2,
                    s * 1.1f, f1, false, (rnd.nextFloat() - 0.5f) * 24f, 1f);
            drawDie(c, holdX + s * 0.6f + (rnd.nextFloat() - 0.5f) * j * 2, holdY + (rnd.nextFloat() - 0.5f) * j * 2,
                    s * 1.1f, f2, false, (rnd.nextFloat() - 0.5f) * 24f, 1f);
            return;
        }

        if (!game.rolled && !rolling) {
            if (humanSide && !game.over) {
                float k = 1f + 0.07f * pulse;
                drawDie(c, cx - s * 0.62f, cy, s * k, 6, false, 0f, 0f);
                drawDie(c, cx + s * 0.62f, cy, s * k, 3, false, 0f, 0f);
            }
            return;
        }

        boolean usedA = false, usedB = false;
        int remaining = 0;
        if (!rolling) {
            int[] cnt = new int[7];
            for (Move m : game.playedMoves()) cnt[m.die]++;
            if (game.d1 == game.d2) {
                remaining = 4 - cnt[game.d1];
                if (remaining < 0) remaining = 0;
                usedA = usedB = remaining == 0;
            } else {
                usedA = cnt[game.d1] > 0;
                usedB = cnt[game.d2] > 0;
            }
        }
        float x0, y0, x1, y1, a0, a1;
        int f0, f1b;
        float lift0 = 0f, lift1 = 0f;
        if (diceLanded || rolling) {
            x0 = dX[0]; y0 = dY[0]; x1 = dX[1]; y1 = dY[1];
            a0 = dAng[0]; a1 = dAng[1];
            f0 = rolling ? dFace[0] : game.d1;
            f1b = rolling ? dFace[1] : game.d2;
            if (rolling) {
                lift0 = Math.max(0f, 1f - rollT * 1.6f);
                lift1 = lift0;
            }
        } else {
            x0 = cx - s * 0.62f; y0 = cy; x1 = cx + s * 0.62f; y1 = cy;
            a0 = a1 = 0f;
            f0 = game.d1;
            f1b = game.d2;
        }
        drawDie(c, x0, y0, s, f0, usedA, a0, lift0);
        drawDie(c, x1, y1, s, f1b, usedB, a1, lift1);
        if (!rolling && game.d1 == game.d2 && remaining > 0 && remaining < 4) {
            tp.setColor(theme.accent);
            tp.setTextSize(s * 0.4f);
            float ly = Math.min(Math.max(y0, y1) + s * 0.95f, it + ih - 6f);
            c.drawText("x" + remaining, (x0 + x1) / 2f, ly, tp);
        }
    }

    private void drawDie(Canvas c, float cx, float cy, float s, int face, boolean dim, float angleDeg, float lift) {
        Skins.drawDie(c, p, rect, cx, cy, s, face, dim, angleDeg, lift, diceSkin);
    }

    private static final boolean[][][] PIPS = new boolean[7][3][3];

    static {
        int[][] pos = {
                {},
                {4},
                {0, 8},
                {0, 4, 8},
                {0, 2, 6, 8},
                {0, 2, 4, 6, 8},
                {0, 2, 3, 5, 6, 8}
        };
        for (int f = 1; f <= 6; f++) {
            for (int k : pos[f]) PIPS[f][k / 3][k % 3] = true;
        }
    }

    // ------------------------------------------------------------ animasyonlar

    /** Zar atma animasyonu; bitince 'done' çalışır. Sonuç zaten Game tarafından belirlenmiştir. */
    public void animateDice(final Runnable done) {
        rolling = true;
        selectedFrom = -1;
        hintMove = null;
        final boolean humanSide = game.turn == Game.HUMAN;
        final float s = diceSize();
        diceS = s;
        final float zl = humanSide ? il + 7 * colW : il;
        final float zr = humanSide ? trayLeft() : il + 6 * colW;
        final float minX = zl + s * 0.55f, maxX = zr - s * 0.55f;
        final float minY = it + s * 0.55f + 4, maxY = it + ih - s * 0.55f - 4;
        final float u = colW;

        float sx, sy, vx, vy;
        if (pendingThrow) {
            sx = Math.max(minX, Math.min(maxX, throwX));
            sy = Math.max(minY, Math.min(maxY, throwY));
            if (throwHasVel) {
                vx = throwVx;
                vy = throwVy;
            } else {
                vx = (humanSide ? -0.6f : 0.6f) * 22f * u;
                vy = (humanSide ? -1f : 1f) * 22f * u;
            }
        } else {
            sx = humanSide ? maxX : minX;
            sy = humanSide ? maxY : minY;
            float dir = 0.4f + rnd.nextFloat() * 0.5f;
            vx = (humanSide ? -dir : dir) * 24f * u;
            vy = (humanSide ? -1f : 1f) * (18f + rnd.nextFloat() * 10f) * u;
        }
        pendingThrow = false;
        double sp = Math.hypot(vx, vy);
        double clamped = Math.max(16f * u, Math.min(42f * u, sp));
        if (sp > 0.001) {
            vx *= clamped / sp;
            vy *= clamped / sp;
        }
        for (int i = 0; i < 2; i++) {
            double ang = (rnd.nextFloat() - 0.5f) * 0.5f * (i == 0 ? -1 : 1);
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            dVx[i] = vx * cs - vy * sn;
            dVy[i] = vx * sn + vy * cs;
            dX[i] = sx + (i == 0 ? -s * 0.35f : s * 0.35f);
            dY[i] = sy + (i == 0 ? s * 0.2f : -s * 0.2f);
            dX[i] = Math.max(minX, Math.min(maxX, dX[i]));
            dY[i] = Math.max(minY, Math.min(maxY, dY[i]));
            dAng[i] = rnd.nextFloat() * 360f;
            dAngV[i] = (rnd.nextBoolean() ? 1 : -1) * (500f + rnd.nextFloat() * 500f);
            dTilt[i] = (rnd.nextFloat() - 0.5f) * 22f;
            dSpin[i] = 0f;
            dFace[i] = rnd.nextInt(6) + 1;
        }
        rollT = 0f;
        final long start = System.nanoTime();
        final long[] last = {start};
        handler.post(new Runnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                float dt = Math.min(0.033f, (now - last[0]) / 1e9f);
                last[0] = now;
                float t = (now - start) / 1e9f;
                float damp = (float) Math.exp(-(t < 0.9f ? 1.6f : 5f) * dt);
                boolean slow = true;
                for (int i = 0; i < 2; i++) {
                    dX[i] += dVx[i] * dt;
                    dY[i] += dVy[i] * dt;
                    dVx[i] *= damp;
                    dVy[i] *= damp;
                    dAng[i] += dAngV[i] * dt;
                    dAngV[i] *= damp;
                    boolean hit = false;
                    if (dX[i] < minX) { dX[i] = minX; dVx[i] = Math.abs(dVx[i]) * 0.7f; hit = true; }
                    if (dX[i] > maxX) { dX[i] = maxX; dVx[i] = -Math.abs(dVx[i]) * 0.7f; hit = true; }
                    if (dY[i] < minY) { dY[i] = minY; dVy[i] = Math.abs(dVy[i]) * 0.7f; hit = true; }
                    if (dY[i] > maxY) { dY[i] = maxY; dVy[i] = -Math.abs(dVy[i]) * 0.7f; hit = true; }
                    if (hit) {
                        dAngV[i] += (rnd.nextFloat() - 0.5f) * 600f;
                        dFace[i] = rnd.nextInt(6) + 1;
                        bounceSfx();
                    }
                    // dönerken yüz değiştir
                    dSpin[i] += Math.abs(dAngV[i]) * dt + (float) Math.hypot(dVx[i], dVy[i]) * dt * 2f;
                    if (dSpin[i] > 90f) {
                        dSpin[i] = 0f;
                        dFace[i] = rnd.nextInt(6) + 1;
                    }
                    double spd = Math.hypot(dVx[i], dVy[i]);
                    if (spd > 3f * u) slow = false;
                    if (spd < 6f * u) {
                        // durulurken gerçek yüze ve hafif eğime yerleş
                        dFace[i] = i == 0 ? game.d1 : game.d2;
                        float target = Math.round(dAng[i] / 90f) * 90f + dTilt[i];
                        dAng[i] += (target - dAng[i]) * Math.min(1f, dt * 14f);
                        dAngV[i] *= 0.8f;
                    }
                }
                // zar-zar çarpışması
                float ddx = dX[1] - dX[0], ddy = dY[1] - dY[0];
                float dist = (float) Math.hypot(ddx, ddy);
                float minD = s * 0.95f;
                if (dist < minD && dist > 0.001f) {
                    float nx = ddx / dist, ny = ddy / dist;
                    float push = (minD - dist) / 2f;
                    dX[0] -= nx * push; dY[0] -= ny * push;
                    dX[1] += nx * push; dY[1] += ny * push;
                    float v0 = dVx[0] * nx + dVy[0] * ny, v1 = dVx[1] * nx + dVy[1] * ny;
                    float diff = (v0 - v1) * 0.85f;
                    if (diff > 0) {
                        dVx[0] -= nx * diff; dVy[0] -= ny * diff;
                        dVx[1] += nx * diff; dVy[1] += ny * diff;
                        bounceSfx();
                    }
                }
                rollT = Math.min(1f, t / 1.4f);
                invalidate();
                if ((t > 0.55f && slow) || t > 2.2f) {
                    for (int i = 0; i < 2; i++) {
                        dAng[i] = Math.round(dAng[i] / 90f) * 90f + dTilt[i];
                        dVx[i] = dVy[i] = dAngV[i] = 0f;
                    }
                    rolling = false;
                    diceLanded = true;
                    rollT = 1f;
                    invalidate();
                    if (done != null) done.run();
                    return;
                }
                handler.postDelayed(this, 16);
            }
        });
    }

    private void bounceSfx() {
        long now = System.currentTimeMillis();
        if (now - lastBounceSfx > 90) {
            lastBounceSfx = now;
            Sfx.bounce();
        }
    }

    /** Hamle zaten Game.play ile uygulandı; taşın kaynaktan hedefe kayışını gösterir. */
    public void animateMove(final int player, Move m, final Runnable done) {
        Board b = game.board;
        float[] src;
        if (m.from == 25) {
            src = new float[]{barX(), barY(player, b.bar[player])};
        } else {
            int sp = screenPointOf(player, m.from);
            int slot = b.pts[player][m.from];
            src = new float[]{colX(colOf(sp)), stackY(sp <= 12, slot)};
        }
        float[] dst;
        if (m.to <= 0) {
            float cx = trayLeft() + (il + iw - trayLeft()) / 2f;
            dst = new float[]{cx, player == Game.HUMAN ? it + ih - r * 1.4f : it + r * 1.4f};
            hideTray = true;
            hideScreenPoint = -1;
            hidePlayer = -1;
        } else {
            int dp = screenPointOf(player, m.to);
            int slot = b.pts[player][m.to] - 1;
            dst = new float[]{colX(colOf(dp)), stackY(dp <= 12, slot)};
            hideScreenPoint = dp;
            hidePlayer = player;
            hideTray = false;
        }
        mx0 = src[0];
        my0 = src[1];
        mx1 = dst[0];
        my1 = dst[1];
        movPlayer = player;
        moving = true;
        selectedFrom = -1;
        hintMove = null;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(player == Game.HUMAN ? 230 : 330);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                mProg = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                moving = false;
                hideScreenPoint = -1;
                hidePlayer = -1;
                hideTray = false;
                invalidate();
                if (done != null) done.run();
            }
        });
        va.start();
    }

    // ------------------------------------------------------------ dokunma

    private boolean canRoll() {
        return game != null && !locked && !game.over && !moving && !rolling
                && game.turn == Game.HUMAN && !game.rolled;
    }

    private boolean canPlay() {
        return game != null && !locked && !game.over && !moving && !rolling
                && game.turn == Game.HUMAN && game.rolled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                dragging = false;
                dragFrom = -1;
                if (canRoll()) {
                    holding = true;
                    holdX = x;
                    holdY = y;
                    holdVx = holdVy = 0f;
                    holdT = System.nanoTime();
                    invalidate();
                    return true;
                }
                if (canPlay()) {
                    int t = hitTest(x, y);
                    if (t >= 1) {
                        for (Move m : game.nextMoves()) {
                            if (m.from == t) {
                                dragFrom = t;
                                break;
                            }
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (holding) {
                    long nt = System.nanoTime();
                    float dts = Math.max(0.004f, (nt - holdT) / 1e9f);
                    holdT = nt;
                    holdVx = holdVx * 0.6f + ((x - holdX) / dts) * 0.4f;
                    holdVy = holdVy * 0.6f + ((y - holdY) / dts) * 0.4f;
                    holdX = x;
                    holdY = y;
                    invalidate();
                    return true;
                }
                if (dragFrom != -1 && canPlay()) {
                    float slop = colW * 0.35f;
                    if (!dragging && Math.hypot(x - downX, y - downY) > slop) {
                        dragging = true;
                        selectedFrom = dragFrom;
                    }
                    if (dragging) {
                        dragX = x;
                        dragY = y;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                holding = false;
                dragging = false;
                dragFrom = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (holding) {
                    holding = false;
                    double sp = Math.hypot(holdVx, holdVy);
                    boolean flick = sp > colW * 8f && Math.hypot(x - downX, y - downY) > colW * 0.6f;
                    pendingThrow = true;
                    throwX = x;
                    throwY = y;
                    throwHasVel = flick;
                    throwVx = holdVx;
                    throwVy = holdVy;
                    if (listener != null) listener.onRollRequested();
                    pendingThrow = false;
                    invalidate();
                    return true;
                }
                if (dragging) {
                    dragging = false;
                    int from = dragFrom;
                    dragFrom = -1;
                    int target = hitTest(x, y);
                    Move best = null;
                    if (canPlay()) {
                        for (Move m : game.nextMoves()) {
                            if (m.from != from) continue;
                            boolean match = (target == 0 && m.to <= 0) || (target >= 1 && target <= 24 && m.to == target);
                            if (match && (best == null || m.die < best.die)) best = m;
                        }
                    }
                    if (best != null) {
                        selectedFrom = -1;
                        invalidate();
                        if (listener != null) listener.onMoveChosen(best);
                    } else {
                        invalidate();
                    }
                    return true;
                }
                dragFrom = -1;
                handleTap(x, y);
                performClick();
                return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Dokunulan yeri çöz: 1..24 nokta, 25 bar, 0 tepsi, -1 hiçbiri. */
    private int hitTest(float x, float y) {
        if (x >= trayLeft()) return 0;
        if (x < il || x > il + iw) return -1;
        int col = (int) ((x - il) / colW);
        if (col < 0 || col > 12) return -1;
        if (col == 6) return 25;
        boolean bottom = y > midY;
        if (bottom) return col <= 5 ? 12 - col : 13 - col;
        return col <= 5 ? 13 + col : 12 + col;
    }

    private void handleTap(float x, float y) {
        if (game == null || locked || game.over || moving || rolling) return;
        if (game.turn != Game.HUMAN) return;
        if (!game.rolled) {
            if (listener != null) listener.onRollRequested();
            return;
        }
        int target = hitTest(x, y);
        if (target < 0) {
            selectedFrom = -1;
            invalidate();
            return;
        }
        List<Move> moves = game.nextMoves();

        if (selectedFrom != -1) {
            // hedef mi seçildi?
            Move best = null;
            for (Move m : moves) {
                if (m.from != selectedFrom) continue;
                boolean match = (target == 0 && m.to <= 0) || (target >= 1 && target <= 24 && m.to == target);
                if (match && (best == null || m.die < best.die)) best = m;
            }
            if (best != null) {
                selectedFrom = -1;
                invalidate();
                if (listener != null) listener.onMoveChosen(best);
                return;
            }
            // aynı kaynağa tekrar dokunma: tek hedef varsa oyna
            if (target == selectedFrom) {
                Move only = null;
                int n = 0;
                for (Move m : moves) {
                    if (m.from == selectedFrom) {
                        if (only == null || m.die < only.die) only = m;
                        n++;
                    }
                }
                if (only != null && n == 1) {
                    selectedFrom = -1;
                    invalidate();
                    if (listener != null) listener.onMoveChosen(only);
                    return;
                }
            }
        }
        boolean has = false;
        for (Move m : moves) if (m.from == target) has = true;
        if (!has && selectedFrom == -1 && target >= 0) {
            // doğrudan hedefe dokunma: tek kaynak varsa o taşı oynat
            int src = -2;
            Move pick = null;
            for (Move m : moves) {
                boolean match = (target == 0 && m.to <= 0) || (target >= 1 && m.to == target);
                if (!match) continue;
                if (src == -2) src = m.from;
                else if (src != m.from) src = -3;
                if (pick == null || m.die < pick.die) pick = m;
            }
            if (pick != null && src >= 0) {
                invalidate();
                if (listener != null) listener.onMoveChosen(pick);
                return;
            }
        }
        selectedFrom = has ? target : -1;
        invalidate();
    }
}
