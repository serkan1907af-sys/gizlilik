package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ProActivity extends Activity {
    private Button btnBuy, btnRestore;
    private TextView tvThanks;
    private boolean wasPro;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            if (Prefs.pro() && !wasPro) {
                wasPro = true;
                Toast.makeText(ProActivity.this, R.string.pro_thanks, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wasPro = Prefs.pro();
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        root.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(this, 28);
        root.setPadding(pad, pad, pad, pad);

        TextView star = UiKit.text(this, "⭐", 56, UiKit.GOLD, false);
        star.setGravity(Gravity.CENTER);
        root.addView(star);
        TextView title = UiKit.text(this, getString(R.string.pro_title), 30, UiKit.GOLD, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(UiKit.spacer(this, 18));
        TextView feat = UiKit.text(this, getString(R.string.pro_features), 17, UiKit.CREAM, false);
        feat.setLineSpacing(0, 1.15f);
        root.addView(feat);
        root.addView(UiKit.spacer(this, 22));

        tvThanks = UiKit.text(this, getString(R.string.pro_thanks), 18, UiKit.GOLD, true);
        tvThanks.setGravity(Gravity.CENTER);
        root.addView(tvThanks);

        btnBuy = UiKit.button(this, getString(R.string.pro_buy), UiKit.GOLD, 0xFF000000);
        btnBuy.setTextSize(19);
        btnBuy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ProManager pm = ProManager.get();
                if (pm == null || !pm.buy(ProActivity.this)) {
                    Toast.makeText(ProActivity.this, R.string.pro_error, Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(btnBuy, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnRestore = UiKit.button(this, getString(R.string.pro_restore), UiKit.PANEL, UiKit.CREAM);
        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ProManager.get() != null) ProManager.get().restore();
            }
        });
        LinearLayout.LayoutParams rl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.setMargins(0, UiKit.dp(this, 10), 0, 0);
        root.addView(btnRestore, rl);

        Button close = UiKit.button(this, getString(R.string.close), UiKit.PANEL, UiKit.MUTED);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams cl = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.setMargins(0, UiKit.dp(this, 10), 0, 0);
        root.addView(close, cl);

        setContentView(root);
        UiKit.applyInsets(root, 28);
        refreshUi();
        if (ProManager.get() != null) ProManager.get().addListener(refresh);
    }

    private void refreshUi() {
        boolean pro = Prefs.pro();
        tvThanks.setVisibility(pro ? View.VISIBLE : View.GONE);
        btnBuy.setVisibility(pro ? View.GONE : View.VISIBLE);
        btnRestore.setVisibility(pro ? View.GONE : View.VISIBLE);
        ProManager pm = ProManager.get();
        String price = pm == null ? null : pm.price();
        btnBuy.setText(price == null ? getString(R.string.pro_buy) : getString(R.string.pro_buy_fmt, price));
    }

    @Override
    protected void onDestroy() {
        if (ProManager.get() != null) ProManager.get().removeListener(refresh);
        super.onDestroy();
    }
}
