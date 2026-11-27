package com.parabolsoft.retrokonsol;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Ortak arayuz yardimcilari. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF101015, CARD = 0xFF1C1C25, ACCENT = 0xFF7C6CFF, DIM = 0xFF9A9AA8,
            BTN = 0xFF2A2A36, GOLD = 0xFFFFC857;

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radiusPx);
        g.setColor(color);
        return g;
    }

    static Button button(Context c, String label, boolean primary, View.OnClickListener l) {
        Button btn = new Button(c);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14.5f);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        btn.setBackground(round(primary ? ACCENT : BTN, dp(c, 12)));
        if (l != null) btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 8);
        btn.setLayoutParams(lp);
        return btn;
    }

    /** Kart olusturur ve parent'a ekler. */
    static LinearLayout card(Context c, LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16));
        card.setBackground(round(CARD, dp(c, 16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 16);
        card.setLayoutParams(lp);
        if (title != null) card.addView(text(c, title, 19, Color.WHITE, true));
        parent.addView(card);
        return card;
    }

    static void note(Context c, LinearLayout parent, String t) {
        TextView v = text(c, t, 13, DIM, false);
        v.setPadding(0, dp(c, 8), 0, 0);
        v.setLineSpacing(0, 1.12f);
        parent.addView(v);
    }

    /** Kaydirilabilir sayfa; content bir LinearLayout'tur. */
    static ScrollView page(Context c, LinearLayout content) {
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c, 18), dp(c, 24), dp(c, 18), dp(c, 28));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        sv.setFitsSystemWindows(true); // Android 15+: ekran kenardan kenara
        return sv;
    }
}
