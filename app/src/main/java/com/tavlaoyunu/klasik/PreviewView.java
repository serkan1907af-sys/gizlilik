package com.tavlaoyunu.klasik;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.tavlaoyunu.klasik.engine.Game;

/** Mağazadaki küçük önizleme (zar, taş, tahta, avatar). */
public class PreviewView extends View {
    private final Economy.Item item;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap b0, b1;
    private int bw = -1;

    public PreviewView(Context c, Economy.Item item) {
        super(c);
        this.item = item;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        switch (item.kind) {
            case Economy.DICE: {
                float s = Math.min(w, h) * 0.6f;
                Skins.drawDie(c, p, rect, cx - s * 0.42f, cy + s * 0.05f, s, 5, false, -8f, 0f, item);
                Skins.drawDie(c, p, rect, cx + s * 0.5f, cy - s * 0.05f, s * 0.8f, 2, false, 10f, 0f, item);
                break;
            }
            case Economy.CHECKER: {
                float r = Math.min(w * 0.22f, h * 0.36f);
                int size = (int) Math.ceil(r * 2.6f);
                if (b0 == null || bw != size) {
                    BoardTheme th = BoardTheme.get(Prefs.theme());
                    b0 = Skins.makeChecker(Game.HUMAN, r, size, th, item);
                    b1 = Skins.makeChecker(Game.CPU, r, size, th, item);
                    bw = size;
                }
                c.drawBitmap(b0, cx - r * 1.15f - size / 2f, cy - size / 2f, null);
                c.drawBitmap(b1, cx + r * 1.15f - size / 2f, cy - size / 2f, null);
                break;
            }
            case Economy.BOARD: {
                BoardTheme t = BoardTheme.get(item.boardId);
                p.setStyle(Paint.Style.FILL);
                p.setColor(t.frame);
                rect.set(w * 0.08f, h * 0.1f, w * 0.92f, h * 0.9f);
                c.drawRoundRect(rect, 12f, 12f, p);
                p.setColor(t.board);
                rect.set(w * 0.12f, h * 0.16f, w * 0.88f, h * 0.84f);
                c.drawRoundRect(rect, 8f, 8f, p);
                float tw = (w * 0.76f) / 6f;
                Path path = new Path();
                for (int i = 0; i < 6; i++) {
                    p.setColor(i % 2 == 0 ? t.pointA : t.pointB);
                    float x = w * 0.12f + i * tw;
                    path.reset();
                    path.moveTo(x, h * 0.16f);
                    path.lineTo(x + tw, h * 0.16f);
                    path.lineTo(x + tw / 2f, h * 0.48f);
                    path.close();
                    c.drawPath(path, p);
                    p.setColor(i % 2 == 0 ? t.pointB : t.pointA);
                    path.reset();
                    path.moveTo(x, h * 0.84f);
                    path.lineTo(x + tw, h * 0.84f);
                    path.lineTo(x + tw / 2f, h * 0.52f);
                    path.close();
                    c.drawPath(path, p);
                }
                p.setColor(t.white);
                c.drawCircle(w * 0.3f, h * 0.76f, tw * 0.32f, p);
                p.setColor(t.black);
                c.drawCircle(w * 0.7f, h * 0.24f, tw * 0.32f, p);
                break;
            }
            default: {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x33FFFFFF);
                c.drawCircle(cx, cy, Math.min(w, h) * 0.42f, p);
                p.setColor(0xFFFFFFFF);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(Math.min(w, h) * 0.55f);
                c.drawText(item.emoji == null ? "" : item.emoji, cx, cy + Math.min(w, h) * 0.19f, p);
                break;
            }
        }
    }
}
