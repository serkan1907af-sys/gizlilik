package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Günlük ödül (7 günlük seri) ve günlük görevler. */
public class RewardsActivity extends Activity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        UiKit.applyInsets(root, 14);

        LinearLayout top = UiKit.horizontal(this);
        TextView back = UiKit.text(this, "←", 26, UiKit.CREAM, true);
        back.setPadding(UiKit.dp(this, 6), 0, UiKit.dp(this, 16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        top.addView(UiKit.text(this, Economy.T("Görevler ve Ödüller", "Missions & Rewards"), 22, UiKit.GOLD, true));
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        content = UiKit.vertical(this);
        sv.addView(content);
        root.addView(sv, UiKit.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        content.removeAllViews();
        // günlük ödül
        TextView t1 = UiKit.text(this, Economy.T("🎁 Günlük ödül", "🎁 Daily reward"), 18, UiKit.CREAM, true);
        t1.setPadding(0, UiKit.dp(this, 14), 0, UiKit.dp(this, 8));
        content.addView(t1);
        LinearLayout strip = UiKit.horizontal(this);
        int idx = Economy.dailyIndex();
        boolean can = Economy.canClaimDaily();
        for (int i = 0; i < 7; i++) {
            boolean claimed = can ? i < idx : i <= idx;
            boolean today = can && i == idx;
            TextView d = UiKit.text(this, (i + 1) + "\n🪙\n" + (Economy.DAILY[i] * Economy.mult()), 12,
                    today ? Color.BLACK : UiKit.CREAM, true);
            d.setGravity(Gravity.CENTER);
            d.setBackground(UiKit.card(this, today ? UiKit.GOLD : (claimed ? 0xFF2E7D32 : UiKit.PANEL), UiKit.GOLD, today ? 2 : 0));
            int p = UiKit.dp(this, 6);
            d.setPadding(0, p, 0, p);
            LinearLayout.LayoutParams lp = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(UiKit.dp(this, 2), 0, UiKit.dp(this, 2), 0);
            strip.addView(d, lp);
        }
        content.addView(strip, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button claim = UiKit.button(this, can ? Economy.T("Ödülü Al", "Claim") : Economy.T("Yarın tekrar gel", "Come back tomorrow"),
                can ? UiKit.GOLD : UiKit.PANEL, can ? Color.BLACK : UiKit.MUTED);
        claim.setEnabled(can);
        claim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int a = Economy.claimDaily();
                Toast.makeText(RewardsActivity.this, "+" + a + " 🪙", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        LinearLayout.LayoutParams cl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.setMargins(0, UiKit.dp(this, 10), 0, 0);
        content.addView(claim, cl);

        // görevler
        TextView t2 = UiKit.text(this, Economy.T("🎯 Günlük görevler", "🎯 Daily missions"), 18, UiKit.CREAM, true);
        t2.setPadding(0, UiKit.dp(this, 22), 0, UiKit.dp(this, 8));
        content.addView(t2);
        for (int i = 0; i < 3; i++) {
            final int mi = i;
            Economy.Mission m = Economy.mission(i);
            LinearLayout card = UiKit.vertical(this);
            card.setBackground(UiKit.card(this, UiKit.PANEL, 0, 0));
            int p = UiKit.dp(this, 14);
            card.setPadding(p, p, p, p);
            LinearLayout line = UiKit.horizontal(this);
            line.addView(UiKit.text(this, m.text(), 16, UiKit.CREAM, true), UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(UiKit.text(this, "🪙 " + (m.reward * Economy.mult()), 16, UiKit.GOLD, true));
            card.addView(line);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(m.target);
            pb.setProgress(Economy.missionProgress(i));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(UiKit.GOLD));
            LinearLayout.LayoutParams pl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 10));
            pl.setMargins(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 4));
            card.addView(pb, pl);
            boolean done = Economy.missionDone(i), cl2 = Economy.missionClaimed(i);
            if (done && !cl2) {
                Button b = UiKit.button(this, Economy.T("Ödülü Al", "Claim"), UiKit.GOLD, Color.BLACK);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int a = Economy.claimMission(mi);
                        Toast.makeText(RewardsActivity.this, "+" + a + " 🪙", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                });
                card.addView(b);
            } else {
                card.addView(UiKit.text(this, cl2 ? Economy.T("✓ Alındı", "✓ Claimed")
                        : (Economy.missionProgress(i) + " / " + m.target), 13, cl2 ? 0xFF66BB6A : UiKit.MUTED, true));
            }
            LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 5));
            content.addView(card, lp);
        }
    }
}
