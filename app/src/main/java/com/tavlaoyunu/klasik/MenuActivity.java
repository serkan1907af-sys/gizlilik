package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MenuActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
        UiKit.applyInsets(sv, 16);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private LinearLayout.LayoutParams row() {
        LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        return lp;
    }

    private void build() {
        root.removeAllViews();

        // ---- oyuncu kartı + jeton
        LinearLayout head = UiKit.horizontal(this);
        head.setBackground(UiKit.card(this, UiKit.PANEL, 0, 0));
        int hp = UiKit.dp(this, 12);
        head.setPadding(hp, hp, hp, hp);
        TextView av = UiKit.text(this, Economy.equipped(Economy.AVATAR).emoji, 30, Color.WHITE, false);
        av.setGravity(Gravity.CENTER);
        av.setBackground(UiKit.round(0x33FFFFFF, 30, this));
        int ap = UiKit.dp(this, 10);
        av.setPadding(ap, ap / 2, ap, ap / 2);
        head.addView(av);
        LinearLayout info = UiKit.vertical(this);
        info.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 12), 0);
        info.addView(UiKit.text(this, Economy.name() + "  ✎", 17, UiKit.CREAM, true));
        int[] lp = Economy.levelProgress();
        info.addView(UiKit.text(this, Economy.T("Seviye ", "Level ") + Economy.level() + "  •  " + lp[0] + "/" + lp[1] + " XP", 12, UiKit.MUTED, false));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(lp[1]);
        pb.setProgress(lp[0]);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(UiKit.GOLD));
        info.addView(pb, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 8)));
        head.addView(info, UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView coins = UiKit.text(this, "🪙 " + Economy.coins() + " ＋", 16, Color.BLACK, true);
        coins.setBackground(UiKit.round(UiKit.GOLD, 20, this));
        coins.setPadding(ap * 2, ap / 2, ap * 2, ap / 2);
        coins.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MenuActivity.this, ShopActivity.class);
                i.putExtra("tab", 4);
                startActivity(i);
            }
        });
        head.addView(coins);
        View.OnClickListener rename = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askName();
            }
        };
        info.setOnClickListener(rename);
        av.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MenuActivity.this, ShopActivity.class);
                i.putExtra("tab", 3);
                startActivity(i);
            }
        });
        root.addView(head, row());

        // ---- başlık
        root.addView(UiKit.spacer(this, 14));
        TextView dice = UiKit.text(this, "🎲", 60, UiKit.CREAM, false);
        dice.setGravity(Gravity.CENTER);
        root.addView(dice);
        TextView title = UiKit.text(this, getString(R.string.app_name), 38, UiKit.GOLD, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = UiKit.text(this, Economy.T("Dürüst zar • Çevrimdışı • Reklamsız seçenek", "Fair dice • Offline • Ad-free option"), 12, UiKit.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(UiKit.spacer(this, 16));

        // ---- oyna
        Button play = UiKit.button(this, "▶  " + getString(R.string.play), UiKit.GOLD, Color.BLACK);
        play.setTextSize(22);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseLevel();
            }
        });
        root.addView(play, row());

        // ---- ödül bandı
        if (Economy.anythingToClaim()) {
            Button gift = UiKit.button(this, Economy.T("🎁 Ödüllerin hazır! Al", "🎁 Rewards ready! Claim"), 0xFF2E7D32, Color.WHITE);
            gift.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MenuActivity.this, RewardsActivity.class));
                }
            });
            root.addView(gift, row());
        }

        // ---- ızgara
        LinearLayout r1 = UiKit.horizontal(this);
        r1.addView(tile("🛒", Economy.T("Mağaza", "Shop"), ShopActivity.class), tileLp());
        r1.addView(tile("🎯", Economy.T("Görevler", "Missions"), RewardsActivity.class), tileLp());
        root.addView(r1, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout r2 = UiKit.horizontal(this);
        r2.addView(tile("📊", getString(R.string.stats), StatsActivity.class), tileLp());
        r2.addView(tile("⚙️", getString(R.string.settings), SettingsActivity.class), tileLp());
        root.addView(r2, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Pro
        Button pro = UiKit.button(this, Prefs.pro() ? getString(R.string.pro_active) : "⭐ " + getString(R.string.go_pro)
                + Economy.T(" — hepsi açık, 2x jeton, reklamsız", " — all unlocked, 2x coins, no ads"), UiKit.PANEL, UiKit.GOLD);
        pro.setTextSize(14);
        pro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, ProActivity.class));
            }
        });
        root.addView(pro, row());
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = UiKit.dp(this, 4);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View tile(String emoji, String label, final Class<?> target) {
        LinearLayout t = UiKit.vertical(this);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UiKit.card(this, UiKit.PANEL, 0xFF4A2F18, 1));
        int p = UiKit.dp(this, 16);
        t.setPadding(p, p, p, p);
        TextView e = UiKit.text(this, emoji, 30, UiKit.CREAM, false);
        e.setGravity(Gravity.CENTER);
        t.addView(e);
        TextView l = UiKit.text(this, label, 15, UiKit.CREAM, true);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MenuActivity.this, target));
            }
        });
        return t;
    }

    private void askName() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(Economy.name());
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(Economy.T("Adın", "Your name"))
                .setView(et)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Economy.setName(et.getText().toString());
                        build();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseLevel() {
        CharSequence[] items = {
                "🧒  " + getString(R.string.level_easy),
                "🧔  " + getString(R.string.level_medium),
                "🎩  " + getString(R.string.level_hard)
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
