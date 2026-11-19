package com.retrooyun.baslatici;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Ayarlar: dokunmatik kontroller, fiziksel tus atamalari, kumanda. */
public class SettingsActivity extends Activity {
    private static final int BG = 0xFF121214, CARD = 0xFF1E1E22, ACCENT = 0xFFE53935, DIM = 0xFF9A9AA2;

    private Settings s;
    private ScrollView sv;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = Settings.load(this);
        sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(24), dp(18), dp(28));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Yerlesim duzenleyicisinden donunce ayarlari tazele
        s = Settings.load(this);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---- yardimcilar ----
    private TextView text(String t, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(CARD);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        c.setLayoutParams(lp);
        c.addView(text(title, 19, Color.WHITE, true));
        content.addView(c);
        return c;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        btn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(primary ? ACCENT : 0xFF2C2C32);
        btn.setBackground(g);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void note(LinearLayout c, String t) {
        TextView v = text(t, 12.5f, DIM, false);
        v.setPadding(0, dp(8), 0, 0);
        c.addView(v);
    }

    private void switchRow(LinearLayout c, String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(14.5f);
        sw.setChecked(s.getb(key, def));
        sw.setPadding(0, dp(10), 0, dp(4));
        sw.setOnCheckedChangeListener((v, on) -> {
            s.setb(key, on);
            s.save();
        });
        c.addView(sw);
    }

    private void seekRow(LinearLayout c, String label, String key, int min, int max, int def) {
        final TextView t = text("", 14.5f, Color.WHITE, false);
        t.setPadding(0, dp(12), 0, 0);
        int cur = Math.max(min, Math.min(max, s.geti(key, def)));
        t.setText(label + ": %" + cur);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean user) {
                t.setText(label + ": %" + (p + min));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                s.seti(key, bar.getProgress() + min);
                s.save();
            }
        });
        c.addView(t);
        c.addView(sb);
    }

    private void choiceRow(LinearLayout c, String label, String[] names, String[] values, String key, String def) {
        String cur = s.gets(key, def);
        int ci = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) ci = i;
        final String[] shown = {names[ci]};
        Button bt = button(label + ":  " + shown[0], false, null);
        bt.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(names, (d, which) -> {
                    s.sets(key, values[which]);
                    s.save();
                    bt.setText(label + ":  " + names[which]);
                })
                .show());
        c.addView(bt);
    }

    // ---- sayfa ----
    private void build() {
        final int keep = sv.getScrollY();
        content.removeAllViews();
        content.addView(text("AYARLAR", 26, ACCENT, true));
        content.addView(button("←  Geri", false, v -> finish()));

        // ---- dokunmatik ----
        LinearLayout t = card("Dokunmatik kontroller");
        for (String k : Actions.ALL_KINDS) {
            if (Actions.quake(k))
                choiceRow(t, "Quake düzeni",
                        new String[]{"Tek çubuk: ileri/geri + dön", "İki çubuk: sol yürü, sağ etrafa bak"},
                        new String[]{"single", "twin"}, "scheme." + k, "twin");
            else
                choiceRow(t, Actions.kindName(k) + " düzeni",
                        new String[]{"Tek çubuk: ileri/geri + dön (sağ el serbest)", "İki çubuk: sol yürü, sağ dön"},
                        new String[]{"single", "twin"}, "scheme." + k, "single");
        }
        seekRow(t, "Hareket hassasiyeti", "sens.move", 40, 200, 100);
        seekRow(t, "Dönüş / bakış hassasiyeti", "sens.turn", 40, 250, 100);
        seekRow(t, "Tuş şeffaflığı", "opacity", 10, 100, 60);
        switchRow(t, "Bakışı ters çevir (yukarı/aşağı)", "invert", false);
        switchRow(t, "Sabit hareket çubuğu (düzenleyicide belirlediğin yerde durur)", "fixed", false);
        choiceRow(t, "Sol tarafa çift dokunuş", Actions.DT_NAMES, Actions.DT_IDS, "dt.left", "none");
        choiceRow(t, "Sağ tarafa çift dokunuş", Actions.DT_NAMES, Actions.DT_IDS, "dt.right", "none");
        note(t, "Çift dokunuş: boş bir yere hızlıca iki kez dokun, ikinci dokunuşta parmağını basılı tutarsan işlev basılı kalır.");
        LinearLayout gy = card("Jiroskop (telefonu çevirerek bakma)");
        switchRow(gy, "Jiroskopla bak / nişan al", "gyro", false);
        seekRow(gy, "Jiroskop hassasiyeti", "gyro.sens", 20, 300, 100);
        switchRow(gy, "Yatay yönü ters çevir (sağa çevirince sola dönüyorsa)", "gyro.invx", false);
        switchRow(gy, "Dikey yönü ters çevir (Quake)", "gyro.invy", false);
        note(gy, "Açınca telefonu hafifçe sağa-sola çevirerek dönersin, yukarı-aşağı eğerek (Quake'te) yukarı-aşağı bakarsın. "
                + "Çubuk da çalışmaya devam eder, ikisi toplanır. Yönler ters çıkarsa yukarıdaki anahtarlarla düzelt.");

        // ---- yerlesim ----
        LinearLayout l = card("Ekran tuşlarının yerleşimi");
        for (String k : Actions.ALL_KINDS)
            l.addView(button(Actions.kindName(k) + " düzenini değiştir", true, v -> edit(k)));
        note(l, "Tuşları sürükle, büyüt/küçült, göster/gizle. Silah seçme şeridi (1, 2, 3…) ve ekran klavyesi düğmesi de buradan taşınır. Hazır düzenler (Sade, Klasik, Çok tuşlu) ve kendi kayıtlı düzenlerin burada.");

        // ---- fiziksel tuslar ----
        LinearLayout p = card("Fiziksel tuşlar ve kumanda");
        switchRow(p, "Ses tuşlarını telefonun dönüşüne göre otomatik çevir", "vol.auto", true);
        switchRow(p, "Ses tuşlarını ters çevir (elle)", "vol.flip", false);
        switchRow(p, "Kumanda bağlıyken ekran tuşlarını gizle", "pad.hide", true);
        note(p, "\"Ses +\" tuşu, telefonu hangi yöne yatırırsan yatır sağ elindeki tuş olur. "
                + "Bu telefonda ters çıkarsa \"ters çevir\" anahtarını aç. Atamaları telefon yatayken yap.");

        for (String k : Actions.ALL_KINDS) bindCard(k, Actions.kindName(k) + " tuş atamaları");

        sv.post(() -> sv.scrollTo(0, keep));
    }

    private void edit(String kind) {
        startActivity(new Intent(this, LayoutEditorActivity.class).putExtra("kind", kind));
    }

    private void bindCard(String kind, String title) {
        LinearLayout c = card(title);
        for (String act : Actions.bindable(kind)) {
            c.addView(button(Actions.label(kind, act) + "  —  " + s.bindText(kind, act), false,
                    v -> capture(kind, act)));
        }
        c.addView(button("Varsayılana dön (Ses + = ateş, Ses − = kullan/zıpla)", false, v -> {
            s.resetBinds(kind);
            s.save();
            build();
        }));
        note(c, "Bir işleve dokun, sonra atamak istediğin tuşa bas: ses tuşu, kumanda düğmesi veya klavye tuşu. "
                + "Aynı tuş başka işlevdeyse ondan alınır. Bir işleve birden fazla tuş atanabilir.");
    }

    private void capture(String kind, String act) {
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(Actions.label(kind, act))
                .setMessage("Şimdi atamak istediğin tuşa bas…\n(Geri tuşu iptal eder.)")
                .setNeutralButton("Temizle", (d, w) -> {
                    s.clearBind(kind, act);
                    s.save();
                    build();
                })
                .setNegativeButton("İptal", null)
                .create();
        dlg.setOnKeyListener((d, code, ev) -> {
            if (code == KeyEvent.KEYCODE_BACK) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                int n = Settings.normalize(this, s, code);
                String taken = s.bindKey(kind, act, n);
                s.save();
                d.dismiss();
                build();
                String msg = Settings.keyName(n) + " → " + Actions.label(kind, act);
                if (taken != null && !taken.equals(act))
                    msg += " ('" + Actions.label(kind, taken) + "' işlevinden alındı)";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        dlg.show();
    }
}
