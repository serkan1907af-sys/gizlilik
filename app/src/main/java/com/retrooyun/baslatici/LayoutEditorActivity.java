package com.retrooyun.baslatici;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Ekran tuslarini surukleyerek yerlestirme, boyutlandirma, gizleme, duzen kaydetme. */
public class LayoutEditorActivity extends Activity implements TouchOverlay.EditListener {
    private TouchOverlay overlay;
    private TextView info;
    private Button visBtn, gridBtn;
    private String kind;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        kind = getIntent().getStringExtra("kind");
        if (kind == null) kind = Games.KIND_DOOM;
        if (!Pro.has(this)) { // duzenleyici Pro ozelligi
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1B1B22);
        overlay = new TouchOverlay(this);
        overlay.setKind(kind);
        overlay.setEditMode(true);
        overlay.setEditListener(this);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(0xCC000000);
        bar.setBackground(g);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        bar.addView(info);

        LinearLayout r1 = row();
        r1.addView(btn("Küçült", v -> overlay.scaleSelected(0.88f)));
        r1.addView(btn("Büyüt", v -> overlay.scaleSelected(1.14f)));
        visBtn = btn("Göster/Gizle", v -> overlay.toggleSelectedVisible());
        r1.addView(visBtn);
        gridBtn = btn("", v -> {
            overlay.setGrid(!overlay.gridOn());
            updateGridText();
        });
        r1.addView(gridBtn);
        bar.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("Hazır düzen", v -> presets()));
        r2.addView(btn("Kaydet", v -> saveDialog()));
        r2.addView(btn("Kayıtlılar", v -> savedDialog()));
        r2.addView(btn("Sıfırla", v -> overlay.resetLayout()));
        Button done = btn("Bitti", v -> finish());
        done.setTextColor(0xFFFFD54F);
        r2.addView(done);
        bar.addView(r2);

        root.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        updateGridText();
        onSelected(null, false);
        hideSystemUi();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(12.5f);
        b.setTextColor(Color.WHITE);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(0xFF3A3A44);
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(dp(3), dp(6), dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void updateGridText() {
        gridBtn.setText(overlay.gridOn() ? "Izgara: açık" : "Izgara: kapalı");
    }

    @Override
    public void onSelected(String name, boolean visible) {
        if (name == null) {
            info.setText("Bir tuşa dokun, sonra sürükle. Düğmelerle büyüt/küçült veya gizle.");
            visBtn.setText("Göster/Gizle");
        } else {
            info.setText("Seçili: " + name + (visible ? "" : "  (gizli)"));
            visBtn.setText(visible ? "Gizle" : "Göster");
        }
    }

    private void presets() {
        new AlertDialog.Builder(this)
                .setTitle("Hazır düzen")
                .setItems(new String[]{"Sade (az tuş)", "Klasik (varsayılan)", "Çok tuşlu (hepsi açık)"},
                        (d, which) -> overlay.applyPreset(which == 0 ? "sade" : which == 1 ? "klasik" : "cok"))
                .show();
    }

    private void saveDialog() {
        final EditText et = new EditText(this);
        et.setHint("Düzen adı");
        new AlertDialog.Builder(this)
                .setTitle("Bu düzeni kaydet")
                .setView(et)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String n = et.getText().toString().trim().replace(';', ' ').replace('=', ' ');
                    if (n.isEmpty()) return;
                    Settings s = overlay.settings();
                    s.saveLayout(kind, n);
                    s.save();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void savedDialog() {
        final Settings s = overlay.settings();
        final List<String> names = s.layoutNames(kind);
        if (names.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Henüz kayıtlı düzenin yok.").setPositiveButton("Tamam", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Kayıtlı düzenler")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    final String n = names.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(n)
                            .setPositiveButton("Yükle", (d2, w2) -> {
                                s.loadLayout(kind, n);
                                s.save();
                                overlay.reloadLayoutFromSettings();
                            })
                            .setNeutralButton("Sil", (d2, w2) -> {
                                s.deleteLayout(kind, n);
                                s.save();
                            })
                            .setNegativeButton("Vazgeç", null)
                            .show();
                })
                .show();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }
}
