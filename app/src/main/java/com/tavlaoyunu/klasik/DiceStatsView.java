package com.tavlaoyunu.klasik;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** İki tarafın zar yüzlerinin yüzde dağılımını yan yana çubuklarla gösterir. */
public class DiceStatsView extends View {
    private final int[] you = new int[7];
    private final int[] cpu = new int[7];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public DiceStatsView(Context c) {
        super(c);
        p.setTextAlign(Paint.Align.CENTER);
    }

    /** Dizilerin 1..6 indeksleri kullanılır. */
    public void setData(int[] youCounts, int[] cpuCounts) {
        System.arraycopy(youCounts, 0, you, 0, 7);
        System.arraycopy(cpuCounts, 0, cpu, 0, 7);
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, UiKit.dp(getContext(), 170));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        long ty = 0, tc = 0;
        for (int f = 1; f <= 6; f++) {
            ty += you[f];
            tc += cpu[f];
        }
        if (ty == 0 && tc == 0) return;
        float[] py = new float[7];
        float[] pc = new float[7];
        float max = 25f;
        for (int f = 1; f <= 6; f++) {
            py[f] = ty == 0 ? 0 : you[f] * 100f / ty;
            pc[f] = tc == 0 ? 0 : cpu[f] * 100f / tc;
            max = Math.max(max, Math.max(py[f], pc[f]));
        }
        float top = 22 * d;
        float bottom = h - 26 * d;
        float chartH = bottom - top;
        float groupW = w / 6f;
        float barW = groupW * 0.30f;

        // 16,7% adil çizgisi
        float fairY = bottom - chartH * (100f / 6f) / max;
        p.setColor(0x66FFFFFF);
        p.setStrokeWidth(1.5f * d);
        for (float x = 0; x < w; x += 10 * d) c.drawLine(x, fairY, x + 5 * d, fairY, p);

        for (int f = 1; f <= 6; f++) {
            float gx = (f - 1) * groupW + groupW / 2f;
            drawBar(c, gx - barW - 1 * d, barW, bottom, chartH, py[f], max, UiKit.GOLD, d);
            drawBar(c, gx + 1 * d, barW, bottom, chartH, pc[f], max, 0xFF90A4AE, d);
            p.setColor(UiKit.CREAM);
            p.setTextSize(14 * d);
            p.setFakeBoldText(true);
            c.drawText(String.valueOf(f), gx, h - 6 * d, p);
        }
        p.setFakeBoldText(false);
    }

    private void drawBar(Canvas c, float x, float bw, float bottom, float chartH, float pct, float max, int color, float d) {
        float hh = chartH * pct / max;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        rect.set(x, bottom - hh, x + bw, bottom);
        c.drawRoundRect(rect, 3 * d, 3 * d, p);
        p.setColor(UiKit.CREAM);
        p.setTextSize(10 * d);
        c.drawText(Math.round(pct) + "%", x + bw / 2f, bottom - hh - 3 * d, p);
    }
}
