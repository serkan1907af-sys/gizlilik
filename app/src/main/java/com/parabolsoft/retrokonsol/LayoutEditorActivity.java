package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ekran tuslarini surukleyerek yerlestirme. Dikey ve yatay duzen ayri kaydedilir. */
public class LayoutEditorActivity extends Activity implements PadView.EditListener {
    private PadView pad;
    private TextView info;
    private Button visBtn, gridBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Sys sys = Sys.byId(getIntent().getStringExtra(GameActivity.EXTRA_SYS));
        if (sys == null) sys = Sys.NES;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B10);
        pad = new PadView(this);
        pad.setSystem(sys);
        pad.setEditListener(this);
        pad.setEditMode(true);
        root.addView(pad, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10));
        panel.setBackground(Ui.round(0xDD1C1C25, Ui.dp(this, 14)));

        info = Ui.text(this, "", 13, Color.WHITE, false);
        info.setGravity(Gravity.CENTER);
        panel.addView(info);

        LinearLayout r1 = row();
        r1.addView(small("−", v -> pad.scaleSelected(0.9f)));
        r1.addView(small("+", v -> pad.scaleSelected(1.1f)));
        visBtn = small("Gizle", v -> pad.toggleSelectedVisible());
        r1.addView(visBtn);
        panel.addView(r1);

        LinearLayout r2 = row();
        gridBtn = small("Izgara", v -> {
            pad.setGrid(!pad.gridOn());
            refreshGrid();
        });
        r2.addView(gridBtn);
        r2.addView(small("Sıfırla", v -> pad.resetLayout()));
        r2.addView(small("Bitti", v -> finish()));
        panel.addView(r2);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(panel, lp);
        setContentView(root);
        refreshGrid();
        onSelected(null, true);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private Button small(String t, View.OnClickListener l) {
        Button bt = Ui.button(this, t, false, l);
        bt.setGravity(Gravity.CENTER);
        bt.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        bt.setMinWidth(0);
        bt.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 3), 0);
        bt.setLayoutParams(lp);
        return bt;
    }

    private void refreshGrid() {
        gridBtn.setText(pad.gridOn() ? "Izgara: açık" : "Izgara: kapalı");
    }

    @Override
    public void onSelected(String name, boolean visible) {
        String hint = pad.orientationName() + " düzen düzenleniyor (diğeri için telefonu çevir).\n";
        info.setText(name == null ? hint + "Bir tuşa dokunup sürükle."
                : hint + name + " seçili — sürükle, +/− ile boyutlandır.");
        visBtn.setText(visible ? "Gizle" : "Göster");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.hide(WindowInsets.Type.systemBars());
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
