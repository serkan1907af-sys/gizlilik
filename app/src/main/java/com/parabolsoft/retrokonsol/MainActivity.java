package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ana ekran: oyun kitapligi, oyun ekleme, rehber ve ayarlar. */
public class MainActivity extends Activity {
    private static final int REQ_FILES = 11, REQ_TREE = 12;

    private LinearLayout content;
    private AlertDialog progress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        content = new LinearLayout(this);
        setContentView(Ui.page(this, content));
        Sys.ensureDirs(this);
        Pro.init(this, () -> runOnUiThread(this::render));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        TextView title = Ui.text(this, "Retro Konsol", 28, Color.WHITE, true);
        content.addView(title);
        content.addView(Ui.text(this, "NES · SNES · Game Boy · Game Boy Color · Game Boy Advance", 13, Ui.DIM, false));

        if (!Pro.has(this)) {
            LinearLayout pc = Ui.card(this, content, "⭐ Retro Konsol Pro");
            pc.setBackground(Ui.round(0xFF2A2440, Ui.dp(this, 16)));
            Ui.note(this, pc, "Sınırsız kayıt noktası, geri sarma, hızlandırma, hile kodları, ekran filtreleri, "
                    + "kumanda/klavye ve tuş düzeni düzenleyicisi. Bir kez al, hep senin olsun.");
            pc.addView(Ui.button(this, "Pro'yu gör", true, v -> Pro.show(this, "Bu özellikler", null)));
        }

        List<RomLib.Rom> roms = RomLib.list(this);
        if (roms.isEmpty()) {
            LinearLayout c = Ui.card(this, content, "Hoş geldin!");
            Ui.note(this, c, "Başlamak için oyun dosyalarını telefona ekle. Hangi dosyaların desteklendiğini, "
                    + "nereye konacağını ve nereden bulunacağını “Kurulum rehberi”nde anlattım.");
            c.addView(Ui.button(this, "📖  Kurulum rehberi", true, v -> startActivity(new Intent(this, HelpActivity.class))));
        }

        LinearLayout add = Ui.card(this, content, "Oyun ekle");
        add.addView(Ui.button(this, "＋  Oyun dosyası seç (.zip de olur)", roms.isEmpty() ? false : true, v -> pickFiles()));
        add.addView(Ui.button(this, "📁  Klasörü tara (hepsini ekler)", false, v -> pickTree()));
        add.addView(Ui.button(this, "📖  Kurulum rehberi", false, v -> startActivity(new Intent(this, HelpActivity.class))));
        add.addView(Ui.button(this, "⚙  Ayarlar", false, v -> startActivity(new Intent(this, SettingsActivity.class))));

        if (!roms.isEmpty()) {
            LinearLayout lib = Ui.card(this, content, "Oyunların (" + roms.size() + ")");
            Ui.note(this, lib, "Oynamak için dokun. Silmek için uzun bas.");
            for (RomLib.Rom r : roms) lib.addView(romRow(r));
        }
    }

    private View romRow(RomLib.Rom r) {
        Button btn = Ui.button(this, r.title + "\n" + r.sys.shortName + " · " + (r.size / 1024) + " KB", false,
                v -> GameActivity.launch(this, r));
        btn.setOnLongClickListener(v -> {
            confirmDelete(r);
            return true;
        });
        return btn;
    }

    private void confirmDelete(RomLib.Rom r) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(r.title)
                .setItems(new String[]{"Oyunu sil (kayıtlar da silinir)", "Sadece kayıtları sil", "Vazgeç"}, (d, w) -> {
                    if (w == 0) {
                        RomLib.delete(this, r, true);
                        render();
                    } else if (w == 1) {
                        RomLib.deleteSavesOnly(this, r);
                        render();
                    }
                }).show();
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_FILES);
    }

    private void pickTree() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FILES) {
            final List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            runImport(() -> RomLib.importUris(this, uris, this::setProgress));
        } else if (req == REQ_TREE && data.getData() != null) {
            final Uri tree = data.getData();
            runImport(() -> RomLib.importTree(this, tree, this::setProgress));
        }
    }

    private interface Job {
        String run();
    }

    private void runImport(Job job) {
        progress = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Oyunlar ekleniyor")
                .setMessage("Hazırlanıyor…")
                .setCancelable(false)
                .create();
        progress.show();
        new Thread(() -> {
            String res;
            try {
                res = job.run();
            } catch (Throwable t) {
                res = "Hata: " + t.getMessage();
            }
            final String fin = res;
            runOnUiThread(() -> {
                if (progress != null) progress.dismiss();
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Bitti").setMessage(fin).setPositiveButton("Tamam", null).show();
                render();
            });
        }, "import").start();
    }

    private void setProgress(String m) {
        runOnUiThread(() -> {
            if (progress != null) progress.setMessage(m);
        });
    }
}
