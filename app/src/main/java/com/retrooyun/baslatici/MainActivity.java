package com.retrooyun.baslatici;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Ana ekran: oyun listesi, dosya ekleme. */
public class MainActivity extends Activity {
    private static final int REQ_WAD = 1, REQ_QUAKE = 2;
    private static final int BG = 0xFF121214, CARD = 0xFF1E1E22, ACCENT = 0xFFE53935, TEXT = 0xFFEDEDED, DIM = 0xFF9A9AA2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView status;
    private boolean busy = false;
    private boolean ready = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(40), dp(18), dp(28));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);

        status = new TextView(this);
        status.setTextColor(DIM);
        status.setTextSize(14);
        render();

        // Ilk acilista motor verisi ve gomulu Freedoom hazirlanir.
        busy = true;
        setStatus("İlk kurulum yapılıyor, birkaç saniye sürebilir…");
        new Thread(() -> {
            try {
                Games.ensureAssets(this);
                ready = true;
                ui.post(() -> {
                    busy = false;
                    setStatus("");
                    render();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy = false;
                    setStatus("Hazırlık başarısız: " + e.getMessage());
                });
            }
        }, "prepare").start();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ---------------- arayuz ----------------
    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(16), dp(10), dp(16), dp(10));
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

    private LinearLayout section(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(CARD);
        card.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(18);
        card.setLayoutParams(lp);
        card.addView(text(title, 20, Color.WHITE, true));
        content.addView(card);
        return card;
    }

    private void render() {
        content.removeAllViews();
        content.addView(text("RETRO OYUN", 28, ACCENT, true));
        content.addView(text("Doom ve Quake, tek uygulamada", 15, DIM, false));
        content.addView(button("⚙  Ayarlar: tuşlar, dokunmatik, düzen", false,
                v -> startActivity(new Intent(this, SettingsActivity.class))));

        // ---- DOOM ----
        LinearLayout doom = section("DOOM");
        File[] wads = Games.doomDir(this).listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".wad"));
        if (wads != null) {
            Arrays.sort(wads);
            for (File f : wads) {
                boolean bundled = f.getName().equals(Games.BUNDLED_WAD);
                final String wadKind = Games.kindForWad(f.getName());
                String label = "▶  " + f.getName() + "   (" + (f.length() / (1024 * 1024)) + " MB)"
                        + (bundled ? "\nÜcretsiz Freedoom – hemen oyna"
                        : wadKind.equals(Games.KIND_DOOM) ? "" : "\n" + Actions.kindName(wadKind));
                Button bt = button(label, bundled, v -> play(wadKind, f.getPath()));
                if (!bundled) {
                    bt.setOnLongClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setMessage(f.getName() + " silinsin mi?")
                                .setPositiveButton("Sil", (d, w) -> {
                                    f.delete();
                                    render();
                                })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                        return true;
                    });
                }
                doom.addView(bt);
            }
        }
        doom.addView(button("＋  Kendi WAD dosyanı ekle", false, v -> pickWad()));
        TextView h1 = text("DOOM.WAD, DOOM2.WAD, HERETIC.WAD, HEXEN.WAD ve modları (WAD) eklenebilir. "
                + "Oyun dosyaları uygulamaya dahil değildir; kendi kopyanı sen eklersin. "
                + "Eklenen dosyayı silmek için uzun bas.", 12.5f, DIM, false);
        h1.setPadding(0, dp(10), 0, 0);
        doom.addView(h1);

        // ---- QUAKE ----
        LinearLayout quake = section("QUAKE");
        if (Games.quakeInstalled(this)) {
            quake.addView(button("▶  Quake 1'i oyna", true,
                    v -> play(Games.KIND_QUAKE, Games.quakePak0(this).getPath())));
            quake.addView(button("Dosyaları yeniden seç", false, v -> pickQuake()));
        } else {
            quake.addView(button("＋  Quake klasörünü seç (id1)", true, v -> pickQuake()));
        }
        TextView h2 = text("Quake'in kendi dosyaları (içinde pak0.pak olan 'id1' klasörü) gerekir. "
                + "Steam/GOG'daki kopyanı telefona atıp klasörü seçmen yeterli.", 12.5f, DIM, false);
        h2.setPadding(0, dp(10), 0, 0);
        quake.addView(h2);

        // ---- durum / bilgi ----
        status.setPadding(0, dp(16), 0, 0);
        if (status.getParent() != null) ((LinearLayout) status.getParent()).removeView(status);
        content.addView(status);

        TextView foot = text("Doom: sol yarıda parmağını bas ve it: yukarı/aşağı = ileri/geri, sağa/sola = dön. "
                + "Sağ elin ateş ve diğer tuşlara serbest kalır.\n"
                + "Quake: sol çubuk = yürü, sağ yarıda çubuk = etrafa bak.\n"
                + "Ses tuşları varsayılan olarak ateş / kullan'dır; Ayarlar'dan değiştirebilirsin. "
                + "Bluetooth kumanda bağlanınca ekran tuşları otomatik gizlenir.\n\n"
                + "Freedoom (BSD lisanslı ücretsiz oyun) dahildir. Motorlar: PrBoom ve TyrQuake (GPL), libretro API.",
                12, DIM, false);
        foot.setPadding(0, dp(22), 0, 0);
        content.addView(foot);
    }

    private void play(String kind, String path) {
        if (!ready || busy) {
            setStatus("Bir saniye, hazırlık sürüyor…");
            return;
        }
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(GameActivity.EXTRA_KIND, kind);
        i.putExtra(GameActivity.EXTRA_PATH, path);
        startActivity(i);
    }

    // ---------------- dosya secme ----------------
    private void pickWad() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_WAD);
    }

    private void pickQuake() {
        if (busy) return;
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_QUAKE);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_WAD) importWad(uri);
        else if (req == REQ_QUAKE) importQuake(uri);
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? "oyun.wad" : p.substring(p.lastIndexOf('/') + 1);
    }

    private static String safeName(String n) {
        return n.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void progressCopy(InputStream in, File out, String label) throws Exception {
        File tmp = new File(out.getPath() + ".tmp");
        long total = 0, lastShown = 0;
        try (OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
                if (total - lastShown > 8L * 1024 * 1024) {
                    lastShown = total;
                    final String s = label + " kopyalanıyor… " + (total / (1024 * 1024)) + " MB";
                    ui.post(() -> setStatus(s));
                }
            }
        }
        out.delete();
        if (!tmp.renameTo(out)) throw new Exception("dosya yazılamadı");
    }

    private void importWad(Uri uri) {
        final String name = displayName(uri);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".wad")) {
            setStatus("Bu bir .wad dosyası değil: " + name);
            return;
        }
        busy = true;
        setStatus(name + " kopyalanıyor…");
        new Thread(() -> {
            String result;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                Games.doomDir(this).mkdirs();
                progressCopy(in, new File(Games.doomDir(this), safeName(name)), name);
                result = name + " eklendi.";
            } catch (Exception e) {
                result = "Kopyalama başarısız: " + e.getMessage();
            }
            final String r = result;
            ui.post(() -> {
                busy = false;
                render();
                setStatus(r);
            });
        }, "import-wad").start();
    }

    private static final class Child {
        String id, name;
        boolean dir;
    }

    private List<Child> listChildren(Uri tree, String parentId) {
        List<Child> out = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        try (Cursor c = getContentResolver().query(kids, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Child k = new Child();
                k.id = c.getString(0);
                k.name = c.getString(1);
                k.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2));
                out.add(k);
            }
        }
        return out;
    }

    private void importQuake(Uri tree) {
        busy = true;
        setStatus("Quake klasörü okunuyor…");
        new Thread(() -> {
            String result;
            try {
                String parent = DocumentsContract.getTreeDocumentId(tree);
                List<Child> kids = listChildren(tree, parent);
                for (Child k : kids) {
                    if (k.dir && k.name.equalsIgnoreCase("id1")) {
                        kids = listChildren(tree, k.id);
                        break;
                    }
                }
                File outDir = Games.quakeId1(this);
                outDir.mkdirs();
                boolean pak0 = false;
                int count = 0;
                for (Child k : kids) {
                    String low = k.name.toLowerCase(Locale.ROOT);
                    if (k.dir || !low.endsWith(".pak")) continue;
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, k.id);
                    try (InputStream in = getContentResolver().openInputStream(doc)) {
                        progressCopy(in, new File(outDir, safeName(low)), k.name);
                    }
                    count++;
                    if (low.equals("pak0.pak")) pak0 = true;
                }
                if (pak0) result = "Quake hazır (" + count + " dosya kopyalandı).";
                else result = "pak0.pak bulunamadı. İçinde pak0.pak olan 'id1' klasörünü seçmelisin.";
            } catch (Exception e) {
                result = "Kopyalama başarısız: " + e.getMessage();
            }
            final String r = result;
            ui.post(() -> {
                busy = false;
                render();
                setStatus(r);
            });
        }, "import-quake").start();
    }
}
