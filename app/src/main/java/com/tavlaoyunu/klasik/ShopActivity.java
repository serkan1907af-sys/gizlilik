package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Mağaza: zar, taş, tahta, avatar ve jeton paketleri. */
public class ShopActivity extends Activity {
    private LinearLayout content;
    private TextView tvCoins;
    private Button[] tabs;
    private int tab = 0;

    private final Runnable billingRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    private static final int[] RARITY_COLOR = {0xFF6D6D78, 0xFF3FA0FF, 0xFFB65CFF, 0xFFFFB300};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tab = getIntent().getIntExtra("tab", 0);
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        UiKit.applyInsets(root, 12);

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
        TextView title = UiKit.text(this, Economy.T("Mağaza", "Shop"), 24, UiKit.GOLD, true);
        top.addView(title, UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvCoins = UiKit.text(this, "", 18, Color.BLACK, true);
        tvCoins.setBackground(UiKit.round(UiKit.GOLD, 20, this));
        int cp = UiKit.dp(this, 10);
        tvCoins.setPadding(cp * 2, cp / 2, cp * 2, cp / 2);
        top.addView(tvCoins);
        root.addView(top, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = UiKit.horizontal(this);
        String[] names = {Economy.T("🎲 Zarlar", "🎲 Dice"), Economy.T("⚫ Taşlar", "⚫ Checkers"),
                Economy.T("🟩 Tahtalar", "🟩 Boards"), Economy.T("🙂 Avatarlar", "🙂 Avatars"),
                Economy.T("🪙 Jeton", "🪙 Coins")};
        tabs = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button t = UiKit.button(this, names[i], UiKit.PANEL, UiKit.CREAM);
            t.setTextSize(14);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    refresh();
                }
            });
            LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, UiKit.dp(this, 6), 0);
            tabRow.addView(t, lp);
            tabs[i] = t;
        }
        hs.addView(tabRow);
        LinearLayout.LayoutParams hl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMargins(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 8));
        root.addView(hs, hl);

        ScrollView sv = new ScrollView(this);
        content = UiKit.vertical(this);
        sv.addView(content);
        root.addView(sv, UiKit.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        if (ProManager.get() != null) ProManager.get().addListener(billingRefresh);
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (ProManager.get() != null) ProManager.get().removeListener(billingRefresh);
        super.onDestroy();
    }

    private void refresh() {
        tvCoins.setText("🪙 " + Economy.coins());
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(UiKit.round(i == tab ? UiKit.GOLD : UiKit.PANEL, 14, this));
            tabs[i].setTextColor(i == tab ? Color.BLACK : UiKit.CREAM);
        }
        content.removeAllViews();
        if (tab == 4) {
            buildCoinTab();
            return;
        }
        if (Prefs.pro()) {
            TextView t = UiKit.text(this, Economy.T("⭐ Pro: tüm eşyalar açık", "⭐ Pro: everything unlocked"), 14, UiKit.GOLD, true);
            t.setPadding(0, 0, 0, UiKit.dp(this, 8));
            content.addView(t);
        }
        List<Economy.Item> list = Economy.items(tab);
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = UiKit.horizontal(this);
                row.setBaselineAligned(false);
                content.addView(row, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            View card = card(list.get(i));
            LinearLayout.LayoutParams lp = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int m = UiKit.dp(this, 4);
            lp.setMargins(m, m, m, m);
            row.addView(card, lp);
        }
        if (list.size() % 2 == 1 && row != null) {
            View pad = new View(this);
            LinearLayout.LayoutParams lp = UiKit.lpWeight(0, 1, 1f);
            row.addView(pad, lp);
        }
    }

    private View card(final Economy.Item it) {
        LinearLayout v = UiKit.vertical(this);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean equipped = Economy.equipped(it.kind).id.equals(it.id);
        v.setBackground(UiKit.card(this, UiKit.PANEL, RARITY_COLOR[it.rarity], equipped ? 3 : 1));
        int p = UiKit.dp(this, 10);
        v.setPadding(p, p, p, p);
        PreviewView pv = new PreviewView(this, it);
        v.addView(pv, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 86)));
        TextView name = UiKit.text(this, it.name(), 15, UiKit.CREAM, true);
        name.setGravity(Gravity.CENTER);
        v.addView(name);
        String[] rn = {Economy.T("Sıradan", "Common"), Economy.T("Nadir", "Rare"), Economy.T("Epik", "Epic"), Economy.T("Efsane", "Legendary")};
        TextView rar = UiKit.text(this, rn[it.rarity], 11, RARITY_COLOR[it.rarity], true);
        rar.setGravity(Gravity.CENTER);
        v.addView(rar);

        boolean owned = Economy.owns(it.id);
        String label;
        if (equipped) label = Economy.T("✓ Kuşanıldı", "✓ Equipped");
        else if (owned) label = Economy.T("Kuşan", "Equip");
        else label = "🪙 " + it.price;
        Button btn = UiKit.button(this, label, equipped ? 0xFF2E7D32 : (owned ? UiKit.GOLD : 0xFF4A2F18), equipped ? Color.WHITE : (owned ? Color.BLACK : UiKit.GOLD));
        btn.setTextSize(14);
        LinearLayout.LayoutParams bl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.setMargins(0, UiKit.dp(this, 6), 0, 0);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int r = Economy.buyOrEquip(it);
                if (r == 1) {
                    Toast.makeText(ShopActivity.this, Economy.T("Jetonun yetmiyor. Görev yap, reklam izle ya da jeton al.", "Not enough coins. Do missions, watch an ad or buy coins."), Toast.LENGTH_LONG).show();
                    tab = 4;
                }
                refresh();
            }
        });
        v.addView(btn, bl);
        return v;
    }

    private void buildCoinTab() {
        // reklamla jeton
        int left = Economy.adsLeft();
        Button ad = UiKit.button(this, Economy.T("🎬 Reklam izle +" + Economy.AD_COINS + " 🪙  (bugün kalan: " + left + ")",
                "🎬 Watch ad +" + Economy.AD_COINS + " 🪙  (left today: " + left + ")"), 0xFF2E7D32, Color.WHITE);
        ad.setEnabled(left > 0);
        ad.setAlpha(left > 0 ? 1f : 0.45f);
        ad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AdManager.showRewarded(ShopActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        Economy.adWatched();
                        Toast.makeText(ShopActivity.this, "+" + Economy.AD_COINS + " 🪙", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ShopActivity.this, R.string.ad_not_ready, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        content.addView(ad, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = UiKit.text(this, Economy.T("Jeton paketleri", "Coin packs"), 18, UiKit.GOLD, true);
        hint.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 6));
        content.addView(hint);

        String[] ids = ProManager.COIN_IDS;
        int[] amounts = ProManager.COIN_AMOUNTS;
        String[] tags = {"", Economy.T("En çok tercih edilen", "Most popular"), Economy.T("En avantajlı", "Best value")};
        for (int i = 0; i < ids.length; i++) {
            final String pid = ids[i];
            LinearLayout row = UiKit.horizontal(this);
            row.setBackground(UiKit.card(this, UiKit.PANEL, i == 1 ? UiKit.GOLD : 0xFF4A2F18, i == 1 ? 2 : 1));
            int p = UiKit.dp(this, 14);
            row.setPadding(p, p, p, p);
            LinearLayout col = UiKit.vertical(this);
            col.addView(UiKit.text(this, "🪙 " + amounts[i], 22, UiKit.GOLD, true));
            if (!tags[i].isEmpty()) col.addView(UiKit.text(this, tags[i], 12, UiKit.MUTED, false));
            row.addView(col, UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ProManager pm = ProManager.get();
            String price = pm == null ? null : pm.priceOf(pid);
            Button buy = UiKit.button(this, price == null ? "—" : price, UiKit.GOLD, Color.BLACK);
            buy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ProManager m = ProManager.get();
                    if (m == null || !m.buy(ShopActivity.this, pid)) {
                        Toast.makeText(ShopActivity.this, R.string.pro_error, Toast.LENGTH_LONG).show();
                    }
                }
            });
            row.addView(buy);
            LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 5));
            content.addView(row, lp);
        }

        if (!Prefs.pro()) {
            Button pro = UiKit.button(this, Economy.T("⭐ Pro: tüm eşyalar açık + 2x jeton + reklamsız", "⭐ Pro: everything unlocked + 2x coins + no ads"), UiKit.PANEL, UiKit.GOLD);
            pro.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(ShopActivity.this, ProActivity.class));
                }
            });
            LinearLayout.LayoutParams lp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 14), 0, 0);
            content.addView(pro, lp);
        }
        TextView note = UiKit.text(this, Economy.T("Jetonlar yalnızca görünüm eşyaları içindir; oyunun gücünü ya da zarları etkilemez.",
                "Coins are for cosmetics only; they never affect gameplay or dice."), 12, UiKit.MUTED, false);
        note.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 12));
        content.addView(note);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) refresh();
    }
}
