package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class StatsActivity extends Activity {
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 20);
        box.setPadding(pad, pad, pad, pad);
        sv.addView(box);
        setContentView(sv);
        UiKit.applyInsets(sv, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void line(String label, String value) {
        LinearLayout row = UiKit.horizontal(this);
        TextView l = UiKit.text(this, label, 16, UiKit.CREAM, false);
        TextView v = UiKit.text(this, value, 16, UiKit.GOLD, true);
        row.addView(l, UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v);
        row.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        box.addView(row);
    }

    private void build() {
        box.removeAllViews();
        box.addView(UiKit.text(this, getString(R.string.stats), 26, UiKit.GOLD, true));
        box.addView(UiKit.spacer(this, 12));

        int games = Prefs.games();
        int wins = Prefs.wins();
        line(getString(R.string.stats_games), String.valueOf(games));
        line(getString(R.string.stats_wins), String.valueOf(wins));
        line(getString(R.string.stats_losses), String.valueOf(games - wins));
        line(getString(R.string.stats_winrate), games == 0 ? "-" : (Math.round(wins * 100f / games) + "%"));

        box.addView(UiKit.spacer(this, 16));
        box.addView(UiKit.text(this, getString(R.string.stats_dice_title), 16, UiKit.CREAM, true));
        TextView legend = UiKit.text(this, "■ " + getString(R.string.legend_you) + "    ■ " + getString(R.string.legend_cpu),
                13, UiKit.MUTED, false);
        box.addView(legend);
        int[] you = new int[7];
        int[] cpu = new int[7];
        for (int f = 1; f <= 6; f++) {
            you[f] = Prefs.face(0, f);
            cpu[f] = Prefs.face(1, f);
        }
        DiceStatsView dv = new DiceStatsView(this);
        dv.setData(you, cpu);
        box.addView(dv, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 170)));
        TextView note = UiKit.text(this, getString(R.string.dice_fair_note), 12, UiKit.MUTED, false);
        box.addView(note);

        box.addView(UiKit.spacer(this, 20));
        if (Prefs.pro()) {
            line(getString(R.string.stats_streak), String.valueOf(Prefs.streak()));
            line(getString(R.string.stats_best_streak), String.valueOf(Prefs.bestStreak()));
            line(getString(R.string.stats_gammons), String.valueOf(Prefs.gammons()));
            box.addView(UiKit.spacer(this, 10));
            box.addView(UiKit.text(this, getString(R.string.stats_by_level), 16, UiKit.CREAM, true));
            String[] names = {getString(R.string.level_easy), getString(R.string.level_medium), getString(R.string.level_hard)};
            for (int lv = 1; lv <= 3; lv++) {
                int g = Prefs.gamesAt(lv);
                int w = Prefs.winsAt(lv);
                line(names[lv - 1], w + " / " + g);
            }
        } else {
            TextView lock = UiKit.text(this, "🔒 " + getString(R.string.stats_locked), 15, UiKit.GOLD, true);
            lock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(StatsActivity.this, ProActivity.class));
                }
            });
            lock.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 10));
            box.addView(lock);
        }
    }
}
