package com.tavlaoyunu.klasik;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.tavlaoyunu.klasik.engine.Game;

/** Zar ve taş çizimleri (oyunda ve mağaza önizlemelerinde ortak kullanılır). */
public final class Skins {
    private Skins() {}

    static final boolean[][][] PIPS = new boolean[7][3][3];

    static {
        int[][] pos = {{}, {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};
        for (int f = 1; f <= 6; f++) {
            for (int k : pos[f]) PIPS[f][k / 3][k % 3] = true;
        }
    }

    public static int shade(int color, float f) {
        int a = color >>> 24;
        int rr = Math.min(255, Math.max(0, (int) (((color >> 16) & 255) * f)));
        int gg = Math.min(255, Math.max(0, (int) (((color >> 8) & 255) * f)));
        int bb = Math.min(255, Math.max(0, (int) ((color & 255) * f)));
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    public static void drawCrown(Canvas c, Paint p, float cx, float cy, float size) {
        Path path = new Path();
        float u = size / 2f;
        path.moveTo(cx - u, cy + u * 0.6f);
        path.lineTo(cx - u, cy - u * 0.5f);
        path.lineTo(cx - u * 0.45f, cy);
        path.lineTo(cx, cy - u * 0.8f);
        path.lineTo(cx + u * 0.45f, cy);
        path.lineTo(cx + u, cy - u * 0.5f);
        path.lineTo(cx + u, cy + u * 0.6f);
        path.close();
        c.drawPath(path, p);
    }

    public static void drawStar(Canvas c, Paint p, float cx, float cy, float size) {
        Path path = new Path();
        float ro = size / 2f, ri = ro * 0.42f;
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? ro : ri;
            float x = cx + (float) Math.cos(a) * rad, y = cy + (float) Math.sin(a) * rad;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, p);
    }

    public static void drawDie(Canvas c, Paint p, RectF rect, float cx, float cy, float s, int face,
                               boolean dim, float angleDeg, float lift, Economy.Item skin) {
        int top = skin.c[0], bot = skin.c[1], pip = skin.c[2];
        c.save();
        c.rotate(angleDeg, cx, cy);
        float h = s / 2f;
        float so = s * (0.05f + 0.16f * lift);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x77000000);
        p.setMaskFilter(new BlurMaskFilter(s * (0.08f + 0.08f * lift), BlurMaskFilter.Blur.NORMAL));
        rect.set(cx - h + so * 0.5f, cy - h + so, cx + h + so * 0.5f, cy + h + so);
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p);
        p.setMaskFilter(null);
        float k = 1f + 0.1f * lift;
        h = h * k;
        p.setShader(new LinearGradient(cx - h, cy - h, cx + h, cy + h,
                dim ? 0xFFA8A8A8 : top, dim ? 0xFF7A7A7A : bot, Shader.TileMode.CLAMP));
        rect.set(cx - h, cy - h, cx + h, cy + h);
        c.drawRoundRect(rect, s * 0.2f * k, s * 0.2f * k, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, s * 0.025f));
        p.setColor(0x55000000);
        c.drawRoundRect(rect, s * 0.2f * k, s * 0.2f * k, p);
        p.setStyle(Paint.Style.FILL);
        float o = s * 0.26f * k;
        float pr = s * 0.088f * k;
        boolean[][] map = PIPS[Math.max(1, Math.min(6, face))];
        int pipLight = dim ? 0xFF666666 : shade(pip, 1.6f);
        int pipDark = dim ? 0xFF2E2E2E : shade(pip, 0.7f);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (!map[row][col]) continue;
                float px = cx + (col - 1) * o, py = cy + (row - 1) * o;
                p.setShader(new RadialGradient(px - pr * 0.3f, py - pr * 0.3f, pr * 1.4f,
                        pipLight, pipDark, Shader.TileMode.CLAMP));
                c.drawCircle(px, py, pr, p);
                p.setShader(null);
            }
        }
        if (skin.c[3] == 1 && !dim) {
            p.setColor(0xFFE0A800);
            drawCrown(c, p, cx, cy - h * 0.82f, s * 0.2f * k);
        }
        c.restore();
    }

    /** Taş bitmap'i (gölge + kenar + dolgu + amblem). */
    public static Bitmap makeChecker(int player, float r, int size, BoardTheme th, Economy.Item ck) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(bm);
        Paint k = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rect = new RectF();
        float cs = size / 2f;
        int fill, edge;
        int[] c = ck.c;
        if (c != null && c[0] != 0) {
            fill = player == Game.HUMAN ? c[0] : c[2];
            edge = player == Game.HUMAN ? c[1] : c[3];
        } else {
            fill = player == Game.HUMAN ? th.white : th.black;
            edge = player == Game.HUMAN ? th.whiteEdge : th.blackEdge;
        }
        int emblem = c == null ? 0 : c[4];
        k.setColor(0x77000000);
        k.setMaskFilter(new BlurMaskFilter(r * 0.18f, BlurMaskFilter.Blur.NORMAL));
        cc.drawCircle(cs + r * 0.06f, cs + r * 0.16f, r * 0.98f, k);
        k.setMaskFilter(null);
        k.setColor(edge);
        cc.drawCircle(cs, cs, r, k);
        k.setShader(new RadialGradient(cs - r * 0.3f, cs - r * 0.35f, r * 1.1f, shade(fill, 1.18f), shade(fill, 0.82f), Shader.TileMode.CLAMP));
        cc.drawCircle(cs, cs, r * 0.88f, k);
        k.setShader(null);
        k.setStyle(Paint.Style.STROKE);
        k.setStrokeWidth(Math.max(1.5f, r * 0.08f));
        k.setColor(edge);
        k.setAlpha(170);
        cc.drawCircle(cs, cs, r * 0.58f, k);
        k.setStyle(Paint.Style.FILL);
        if (emblem != 0) {
            k.setColor(edge);
            k.setAlpha(210);
            if (emblem == 1) drawCrown(cc, k, cs, cs, r * 0.7f);
            else drawStar(cc, k, cs, cs, r * 0.95f);
        }
        k.setColor(0x33FFFFFF);
        k.setAlpha(51);
        rect.set(cs - r * 0.55f, cs - r * 0.78f, cs + r * 0.35f, cs - r * 0.3f);
        cc.drawOval(rect, k);
        return bm;
    }
}
