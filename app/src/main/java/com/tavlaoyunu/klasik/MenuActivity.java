package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MenuActivity extends Activity {
    private Button btnPro;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        root.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(this, 32);
        root.setPadding(pad, pad, pad, pad);

        TextView dice = UiKit.text(this, "🎲", 64, UiKit.CREAM, false);
        dice.setGravity(Gravity.CENTER);
        root.addView(dice);

        TextView title = UiKit.text(this, getString(R.string.app_name), 40, UiKit.GOLD, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(UiKit.spacer(this, 28));

        Button play = UiKit.button(this, getString(R.string.play), UiKit.GOLD, 0xFF000000);
        play.setTextSize(20);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseLevel();
            }
        });
        root.addView(play, row());

        Button stats = UiKit.button(this, getString(R.string.stats), UiKit.PANEL, UiKit.CREAM);
        stats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, StatsActivity.class));
            }
        });
        root.addView(stats, row());

        Button settings = UiKit.button(this, getString(R.string.settings), UiKit.PANEL, UiKit.CREAM);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, SettingsActivity.class));
            }
        });
        root.addView(settings, row());

        btnPro = UiKit.button(this, "", UiKit.PANEL, UiKit.GOLD);
        btnPro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, ProActivity.class));
            }
        });
        root.addView(btnPro, row());

        setContentView(root);
    }

    private LinearLayout.LayoutParams row() {
        LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 7));
        return lp;
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnPro.setText(Prefs.pro() ? R.string.pro_active : R.string.go_pro);
    }

    private void chooseLevel() {
        CharSequence[] items = {
                getString(R.string.level_easy),
                getString(R.string.level_medium),
                getString(R.string.level_hard)
        };
        int checked = Math.max(1, Math.min(3, Prefs.lastLevel())) - 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.level_title)
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        int level = which + 1;
                        Prefs.setLastLevel(level);
                        Intent i = new Intent(MenuActivity.this, GameActivity.class);
                        i.putExtra("level", level);
                        startActivity(i);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
