package com.tavlaoyunu.klasik;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
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
        invalidate();
    }

    public void setTheme(BoardTheme t) {
        theme = t;
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
        triH = ih * 0.40f;
        midBand = ih - 2 * triH;
        midY = it + ih / 2f;
        r = Math.min(colW * 0.46f, triH / 9f);
        step = 2 * r * 0.9f;
        maxShow = Math.max(5, Math.min(10, (int) ((triH - 2 * r - 6) / step) + 1));
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

        // arka plan ve çerçeve
        c.drawColor(theme.bg);
        p.setStyle(Paint.Style.FILL);
        p.setColor(theme.frame);
        rect.set(bl, bt, bl + bw, bt + bh);
        c.drawRoundRect(rect, colW * 0.35f, colW * 0.35f, p);
        p.setColor(theme.board);
        rect.set(il, it, il + iw, it + ih);
        c.drawRoundRect(rect, colW * 0.2f, colW * 0.2f, p);

        // bar ve tepsi
        p.setColor(theme.bar);
        rect.set(il + 6 * colW, it, il + 7 * colW, it + ih);
        c.drawRect(rect, p);
        rect.set(trayLeft(), it, il + iw, it + ih);
        c.drawRect(rect, p);

        // üçgenler
        for (int n = 1; n <= 24; n++) {
            boolean bottom = n <= 12;
            float x = colX(colOf(n));
            float baseY = bottom ? it + ih : it;
            float apexY = bottom ? it + ih - triH : it + triH;
            p.setColor((n % 2 == 1) ? theme.pointA : theme.pointB);
            path.reset();
            path.moveTo(x - colW / 2f + 1, baseY);
            path.lineTo(x + colW / 2f - 1, baseY);
            path.lineTo(x, apexY);
            path.close();
            c.drawPath(path, p);
        }

        // hamle göstergeleri (kaynak halkaları, hedef noktalar)
        drawGuides(c);

        // taşlar
        for (int n = 1; n <= 24; n++) {
            int white = b.pts[Game.HUMAN][n];
            int black = b.pts[Game.CPU][25 - n];
            if (white > 0) {
                int cnt = (hidePlayer == Game.HUMAN && hideScreenPoint == n) ? white - 1 : white;
                drawStack(c, n, Game.HUMAN, cnt);
            } else if (black > 0) {
                int cnt = (hidePlayer == Game.CPU && hideScreenPoint == n) ? black - 1 : black;
                drawStack(c, n, Game.CPU, cnt);
            }
        }
        // bar taşları
        for (int k = 0; k < b.bar[Game.HUMAN]; k++) drawChecker(c, barX(), barY(Game.HUMAN, k), Game.HUMAN);
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
        int fill = player == Game.HUMAN ? theme.white : theme.black;
        int edge = player == Game.HUMAN ? theme.whiteEdge : theme.blackEdge;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawCircle(x + r * 0.08f, y + r * 0.12f, r, p);
        p.setColor(edge);
        c.drawCircle(x, y, r, p);
        p.setColor(fill);
        c.drawCircle(x, y, r * 0.84f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r * 0.08f));
        p.setColor(edge);
        c.drawCircle(x, y, r * 0.55f, p);
        p.setStyle(Paint.Style.FILL);
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
                drawRing(c, xy[0], xy[1], r * (1.12f + 0.12f * pulse), theme.accent, 0.55f);
            }
        } else {
            float[] xy = sourceTop(selectedFrom);
            if (xy != null) drawRing(c, xy[0], xy[1], r * 1.2f, theme.accent, 1f);
            for (Move m : moves) {
                if (m.from != selectedFrom) continue;
                float[] t = targetSpot(m);
                p.setStyle(Paint.Style.FILL);
                p.setColor(withAlpha(theme.accent, 150 + (int) (60 * pulse)));
                c.drawCircle(t[0], t[1], r * 0.62f, p);
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

    private void drawDiceArea(Canvas c) {
        float s = Math.min(midBand * 0.70f, colW * 2.3f);
        boolean humanSide = game.turn == Game.HUMAN;
        float cx = humanSide ? il + 10f * colW : il + 3f * colW; // sağ yarı merkezi 10, sol yarı merkezi 3. kolon
        float cy = midY;

        if (!game.rolled && !rolling) {
            if (humanSide && !game.over) {
                // zar atmaya davet
                float k = 1f + 0.06f * pulse;
                drawDie(c, cx - s * 0.62f, cy, s * k, 6, false, 0f);
                drawDie(c, cx + s * 0.62f, cy, s * k, 3, false, 0f);
            }
            return;
        }

        int a = rolling ? rollFaceA : game.d1;
        int d = rolling ? rollFaceB : game.d2;
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
        float wob = rolling ? rollWobble : 0f;
        drawDie(c, cx - s * 0.62f, cy, s, a, usedA, wob);
        drawDie(c, cx + s * 0.62f, cy, s, d, usedB, -wob);
        if (!rolling && game.d1 == game.d2 && remaining > 0 && remaining < 4) {
            tp.setColor(theme.accent);
            tp.setTextSize(s * 0.42f);
            c.drawText("x" + remaining, cx, cy + s * 0.95f, tp);
        }
    }

    private void drawDie(Canvas c, float cx, float cy, float s, int face, boolean dim, float wobbleDeg) {
        c.save();
        c.rotate(wobbleDeg, cx, cy);
        float h = s / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        rect.set(cx - h + 2, cy - h + 3, cx + h + 2, cy + h + 3);
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p);
        p.setColor(dim ? 0xFF8C8C8C : 0xFFFAF7F0);
        rect.set(cx - h, cy - h, cx + h, cy + h);
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p);
        p.setColor(dim ? 0xFF4A4A4A : 0xFF1B1B1B);
        float o = s * 0.26f;
        float pr = s * 0.085f;
        boolean[][] map = PIPS[Math.max(1, Math.min(6, face))];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (map[row][col]) c.drawCircle(cx + (col - 1) * o, cy + (row - 1) * o, pr, p);
            }
        }
        c.restore();
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
        final long start = System.currentTimeMillis();
        final long dur = 700;
        handler.post(new Runnable() {
            @Override
            public void run() {
                long t = System.currentTimeMillis() - start;
                if (t >= dur) {
                    rolling = false;
                    invalidate();
                    if (done != null) done.run();
                    return;
                }
                rollFaceA = rnd.nextInt(6) + 1;
                rollFaceB = rnd.nextInt(6) + 1;
                rollWobble = (rnd.nextFloat() - 0.5f) * 40f * (1f - (float) t / dur);
                invalidate();
                handler.postDelayed(this, 70);
            }
        });
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

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            handleTap(e.getX(), e.getY());
            performClick();
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
        selectedFrom = has ? target : -1;
        invalidate();
    }
}
