package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private LinearLayout themeBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 20);
        box.setPadding(pad, pad, pad, pad);
        sv.addView(box);

        box.addView(UiKit.text(this, getString(R.string.settings), 26, UiKit.GOLD, true));
        box.addView(UiKit.spacer(this, 12));

        box.addView(toggle(R.string.settings_sound, Prefs.sound(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                Prefs.setSound(on);
            }
        }));
        box.addView(toggle(R.string.settings_vibration, Prefs.vibration(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                Prefs.setVibration(on);
            }
        }));
        box.addView(toggle(R.string.settings_shake, Prefs.shake(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                Prefs.setShake(on);
            }
        }));

        box.addView(UiKit.spacer(this, 18));
        box.addView(UiKit.text(this, getString(R.string.settings_theme), 18, UiKit.CREAM, true));
        themeBox = UiKit.vertical(this);
        box.addView(themeBox);
        setContentView(sv);
        UiKit.applyInsets(sv, 0);
        buildThemes();
    }

    private Switch toggle(int label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(UiKit.CREAM);
        s.setTextSize(17);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 12));
        return s;
    }

    private void buildThemes() {
        themeBox.removeAllViews();
        int current = Prefs.theme();
        if (!Economy.owns("b" + current)) current = 0;
        for (int i = 0; i < BoardTheme.COUNT; i++) {
            final BoardTheme t = BoardTheme.get(i);
            LinearLayout row = UiKit.horizontal(this);
            row.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12));
            row.setBackground(UiKit.round(i == current ? 0xFF4A2F18 : UiKit.PANEL, 12, this));
            LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 6), 0, 0);
            row.setLayoutParams(lp);

            View sw = new View(this);
            sw.setBackground(UiKit.round(t.board, 6, this));
            LinearLayout.LayoutParams slp = UiKit.lp(UiKit.dp(this, 36), UiKit.dp(this, 36));
            slp.setMargins(0, 0, UiKit.dp(this, 14), 0);
            row.addView(sw, slp);

            TextView name = UiKit.text(this, getString(t.nameRes), 17, UiKit.CREAM, i == current);
            row.addView(name, UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String tag = "";
            if (i == current) tag = "✓";
            else if (!Economy.owns("b" + t.id)) tag = "🔒 " + getString(R.string.pro_only);
            row.addView(UiKit.text(this, tag, 15, UiKit.GOLD, true));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!Economy.owns("b" + t.id)) {
                        Toast.makeText(SettingsActivity.this, Economy.T("Bu tema Mağaza'da", "This theme is in the Shop"), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SettingsActivity.this, ShopActivity.class));
                        return;
                    }
                    Prefs.setTheme(t.id);
                    buildThemes();
                }
            });
            themeBox.addView(row);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeBox != null) buildThemes();
    }
}
