package com.tavlaoyunu.klasik;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kod ile arayüz kurmak için küçük yardımcılar. */
public final class UiKit {
    private UiKit() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Button button(Context c, String text, int bg, int fg) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(bg, 14, c));
        b.setStateListAnimator(null);
        int pad = dp(c, 14);
        b.setPadding(pad, dp(c, 10), pad, dp(c, 10));
        return b;
    }

    public static void setEnabledLook(Button b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.4f);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpWeight(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    /** Durum çubuğu ve gezinme çubuğunun altında kalmamak için kenar boşluğu ekler. */
    public static void applyInsets(final View root, final int baseDp) {
        final int base = dp(root.getContext(), baseDp);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(base + in.getSystemWindowInsetLeft(), base + in.getSystemWindowInsetTop(),
                        base + in.getSystemWindowInsetRight(), base + in.getSystemWindowInsetBottom());
                return in;
            }
        });
        root.requestApplyInsets();
    }

    /** Yuvarlak köşeli, isteğe bağlı çerçeveli kart arka planı. */
    public static GradientDrawable card(Context c, int color, int strokeColor, int strokeDp) {
        GradientDrawable g = round(color, 16, c);
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    /** Tıklanabilir kalır ama soluk görünür. */
    public static void setDimmed(Button b, boolean dimmed) {
        b.setAlpha(dimmed ? 0.45f : 1f);
    }

    public static final int BG = Color.parseColor("#1A0F06");
    public static final int PANEL = Color.parseColor("#2E1D0E");
    public static final int GOLD = Color.parseColor("#FFC107");
    public static final int CREAM = Color.parseColor("#F7F2E8");
    public static final int MUTED = Color.parseColor("#B8A58A");
}
