package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Ayarlar: ekran, ses, ekran tuslari, tus atama. */
public class SettingsActivity extends Activity {
    private LinearLayout content;
    private Sys bindSys = Sys.NES;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        content = new LinearLayout(this);
        setContentView(Ui.page(this, content));
        Pro.init(this, () -> runOnUiThread(this::render));
        render();
    }

    private interface Pick {
        void on(int v);
    }

    private void seek(LinearLayout parent, String label, int min, int max, int cur, Pick p) {
        TextView t = Ui.text(this, label + ": " + cur, 14.5f, Color.WHITE, false);
        t.setPadding(0, Ui.dp(this, 12), 0, 0);
        parent.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                t.setText(label + ": " + (v + min));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                p.on(s.getProgress() + min);
            }
        });
        parent.addView(sb);
    }

    private void toggle(LinearLayout parent, String label, String key, boolean def) {
        Settings st = Settings.load(this);
        boolean on = st.getb(key, def);
        parent.addView(Ui.button(this, label + ":  " + (on ? "Açık" : "Kapalı"), false, v -> {
            Settings s = Settings.load(this);
            s.setb(key, !on);
            s.save();
            render();
        }));
    }

    private void cycle(LinearLayout parent, String label, String key, String def, String[] vals, String[] names, boolean needPro) {
        Settings st = Settings.load(this);
        String cur = st.gets(key, def);
        int i = 0;
        for (int k = 0; k < vals.length; k++) if (vals[k].equals(cur)) i = k;
        final int idx = i;
        parent.addView(Ui.button(this, label + ":  " + names[idx] + (needPro && !Pro.has(this) ? "  🔒" : ""), false, v -> {
            if (needPro && !Pro.has(this)) {
                Pro.show(this, label, null);
                return;
            }
            Settings s = Settings.load(this);
            s.sets(key, vals[(idx + 1) % vals.length]);
            s.save();
            render();
        }));
    }

    private void render() {
        content.removeAllViews();
        content.addView(Ui.text(this, "Ayarlar", 26, Color.WHITE, true));
        boolean pro = Pro.has(this);
        Settings st = Settings.load(this);

        LinearLayout pc = Ui.card(this, content, pro ? "⭐ Pro açık" : "⭐ Retro Konsol Pro");
        if (!pro) {
            Ui.note(this, pc, "Bir kerelik satın alma. Tüm Pro özelliklerini açar.");
            pc.addView(Ui.button(this, "Pro'yu gör / satın al", true, v -> Pro.show(this, "Bu özellikler", null)));
        } else {
            Ui.note(this, pc, "Teşekkürler! Tüm özellikler açık.");
        }

        LinearLayout v = Ui.card(this, content, "Görüntü ve ses");
        cycle(v, "Ekran boyutu", "video.mode", "fit", new String[]{"fit", "integer", "stretch"},
                new String[]{"Oranı koru", "Tam sayı ölçek (keskin)", "Ekranı doldur"}, false);
        toggle(v, "Yumuşak görüntü", "video.smooth", false);
        cycle(v, "Filtre", "video.filter", "none", new String[]{"none", "crt", "lcd"},
                new String[]{"Yok", "CRT", "LCD"}, true);
        cycle(v, "Ekran yönü", "orient", "auto", new String[]{"auto", "land", "port"},
                new String[]{"Otomatik", "Yatay", "Dikey"}, false);
        seek(v, "Ses seviyesi", 0, 100, st.geti("volume", 100), x -> {
            Settings s = Settings.load(this);
            s.seti("volume", x);
            s.save();
        });
        toggle(v, "Çıkınca kaldığın yerden devam et", "autoresume", true);

        LinearLayout p = Ui.card(this, content, "Ekran tuşları");
        seek(p, "Saydamlık (%)", 10, 100, st.geti("pad.opacity", 55), x -> {
            Settings s = Settings.load(this);
            s.seti("pad.opacity", x);
            s.save();
        });
        seek(p, "Boyut (%)", 60, 160, st.geti("pad.scale", 100), x -> {
            Settings s = Settings.load(this);
            s.seti("pad.scale", x);
            s.save();
        });
        toggle(p, "Titreşim", "pad.haptic", true);
        toggle(p, "Kumanda bağlıyken ekran tuşlarını gizle", "pad.autohide", true);
        Ui.note(this, p, "Tuşların yerini oyun içinde Menü > Tuş düzenini düzenle (Pro) ile değiştirirsin.");

        LinearLayout k = Ui.card(this, content, "Klavye ve kumanda tuş atama" + (pro ? "" : " 🔒"));
        Ui.note(this, k, "Hiçbir şey atamazsan hazır düzen çalışır: kumandada A/B/X/Y konumuna göre, klavyede ok tuşları + "
                + "Z X A S Q W, Enter = Start, Tab = Select, Boşluk = hızlandır, Backspace = geri sar, Esc = menü.");
        if (!pro) {
            k.addView(Ui.button(this, "Pro ile aç", true, x -> Pro.show(this, "Kumanda, klavye ve tuş atama", null)));
        } else {
            for (Sys s : Sys.ALL) {
                final Sys sy = s;
                k.addView(Ui.button(this, (s == bindSys ? "▼  " : "▶  ") + s.name, s == bindSys, x -> {
                    bindSys = sy;
                    render();
                }));
            }
            for (String act : Actions.bindable(bindSys)) {
                final String a = act;
                k.addView(Ui.button(this, Actions.label(act) + "  →  " + st.bindText(bindSys, act), false, x -> bindDialog(a)));
            }
            k.addView(Ui.button(this, "Bu konsolun atamalarını sıfırla", false, x -> {
                Settings s = Settings.load(this);
                s.resetBinds(bindSys);
                s.save();
                render();
            }));
        }

        content.addView(Ui.button(this, "Kapat", true, x -> finish()));
    }

    private void bindDialog(String act) {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Actions.label(act))
                .setMessage("Klavyede ya da kumandada atamak istediğin tuşa bas.")
                .setNeutralButton("Atamayı temizle", (dd, w) -> {
                    Settings s = Settings.load(this);
                    s.clearBind(bindSys, act);
                    s.save();
                    render();
                })
                .setNegativeButton("İptal", null)
                .create();
        d.setOnKeyListener((dd, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN || code == KeyEvent.KEYCODE_BACK) return false;
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER) return false;
            Settings s = Settings.load(this);
            String taken = s.bindKey(bindSys, act, code);
            s.save();
            dd.dismiss();
            render();
            String msg = Settings.keyName(code) + " → " + Actions.label(act)
                    + (taken != null ? " (önceki: " + Actions.label(taken) + ")" : "");
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        d.show();
    }
}
