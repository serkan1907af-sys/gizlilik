package com.parabolsoft.retrokonsol;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Oyun listesi: tarama, ice aktarma (dosya / klasor / zip), silme. */
final class RomLib {
    private RomLib() {}

    static final class Rom {
        Sys sys;
        File file;
        String title, key;
        long size, last;
    }

    interface Progress {
        void update(String message);
    }

    private static final class Counter {
        int added, same, unsupported, failed;
    }

    static String safeName(String n) {
        return n.replaceAll("[^A-Za-z0-9._ ()\\[\\]-]", "_");
    }

    static String baseName(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(0, i) : fileName;
    }

    /** Kayit dosyalari icin anahtar: konsol + dosya adi (uzantisiz). */
    static String key(Sys s, File f) {
        return s.id + "_" + safeName(baseName(f.getName())).replace(' ', '_');
    }

    /** Listede gorunen ad: uzanti ve (USA) / [!] gibi etiketler atilir. */
    static String cleanTitle(String fileName) {
        String t = baseName(fileName).replace('_', ' ');
        String c = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "").trim();
        return c.isEmpty() ? t.trim() : c;
    }

    static List<Rom> list(Context c) {
        Settings st = Settings.load(c);
        List<Rom> out = new ArrayList<>();
        for (Sys s : Sys.ALL) {
            File[] files = s.romDir(c).listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile() || Sys.forFile(f.getName()) != s) continue;
                Rom r = new Rom();
                r.sys = s;
                r.file = f;
                r.title = cleanTitle(f.getName());
                r.key = key(s, f);
                r.size = f.length();
                r.last = st.getl("last." + r.key, 0);
                out.add(r);
            }
        }
        Collections.sort(out, (a, b) -> {
            if (a.last != b.last) return a.last > b.last ? -1 : 1;
            return a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }

    static void delete(Context c, Rom r, boolean withSaves) {
        r.file.delete();
        if (!withSaves) return;
        deleteByPrefix(Sys.saveDir(c), r.key + ".");
        deleteByPrefix(Sys.stateDir(c), r.key + ".");
        new File(Sys.cheatDir(c), r.key + ".txt").delete();
    }

    static void deleteSavesOnly(Context c, Rom r) {
        deleteByPrefix(Sys.saveDir(c), r.key + ".");
        deleteByPrefix(Sys.stateDir(c), r.key + ".");
    }

    private static void deleteByPrefix(File dir, String prefix) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) if (f.getName().startsWith(prefix)) f.delete();
    }

    // ---------------- ice aktarma ----------------
    static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? "oyun" : p.substring(p.lastIndexOf('/') + 1);
    }

    /** Secilen dosyalari (oyun veya .zip) kopyalar. Ozet metin dondurur. */
    static String importUris(Context c, List<Uri> uris, Progress p) {
        Sys.ensureDirs(c);
        Counter k = new Counter();
        int i = 0;
        for (Uri u : uris) {
            i++;
            String name = displayName(c, u);
            if (p != null) p.update("(" + i + "/" + uris.size() + ") " + name + " ekleniyor…");
            importOne(c, u, name, k);
        }
        return summary(k);
    }

    private static final class Entry {
        Uri uri;
        String name;
    }

    /** Klasoru (alt klasorler dahil) tarar, bulunan oyunlari kopyalar. */
    static String importTree(Context c, Uri tree, Progress p) {
        Sys.ensureDirs(c);
        List<Entry> found = new ArrayList<>();
        try {
            String root = DocumentsContract.getTreeDocumentId(tree);
            scan(c, tree, root, 0, found, p);
        } catch (Exception e) {
            return "Klasör okunamadı: " + e.getMessage();
        }
        if (found.isEmpty()) return "Bu klasörde desteklenen oyun dosyası bulunamadı.";
        Counter k = new Counter();
        int i = 0;
        for (Entry en : found) {
            i++;
            if (p != null) p.update("(" + i + "/" + found.size() + ") " + en.name + " ekleniyor…");
            importOne(c, en.uri, en.name, k);
        }
        return summary(k);
    }

    private static void scan(Context c, Uri tree, String parentId, int depth, List<Entry> out, Progress p) {
        if (depth > 8 || out.size() > 5000) return;
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        List<String[]> dirs = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(kids, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0), name = cur.getString(1), mime = cur.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    dirs.add(new String[]{id, name});
                } else {
                    String low = name.toLowerCase(Locale.ROOT);
                    if (low.endsWith(".zip") || Sys.forFile(name) != null) {
                        Entry e = new Entry();
                        e.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        e.name = name;
                        out.add(e);
                    }
                }
            }
        }
        if (p != null) p.update("Taranıyor… " + out.size() + " oyun bulundu");
        for (String[] d : dirs) scan(c, tree, d[0], depth + 1, out, p);
    }

    private static void importOne(Context c, Uri uri, String name, Counter k) {
        try {
            String low = name.toLowerCase(Locale.ROOT);
            if (low.endsWith(".zip")) {
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("açılamadı");
                    importZip(c, in, k);
                }
                return;
            }
            Sys s = Sys.forFile(name);
            if (s == null) {
                k.unsupported++;
                return;
            }
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("açılamadı");
                store(s, c, name, in, k);
            }
        } catch (Exception e) {
            k.failed++;
        }
    }

    private static void importZip(Context c, InputStream raw, Counter k) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(raw)) {
            ZipEntry e;
            boolean any = false;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                n = n.substring(n.lastIndexOf('/') + 1);
                Sys s = Sys.forFile(n);
                if (s == null) continue;
                any = true;
                store(s, c, n, zin, k);
            }
            if (!any) k.unsupported++;
        }
    }

    private static void store(Sys s, Context c, String name, InputStream in, Counter k) throws IOException {
        File dir = s.romDir(c);
        dir.mkdirs();
        File out = new File(dir, safeName(name));
        File tmp = new File(out.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        if (out.isFile() && out.length() == tmp.length()) {
            tmp.delete();
            k.same++;
            return;
        }
        out.delete();
        if (!tmp.renameTo(out)) throw new IOException("dosya yazılamadı");
        k.added++;
    }

    private static String summary(Counter k) {
        StringBuilder sb = new StringBuilder();
        sb.append(k.added).append(" oyun eklendi");
        if (k.same > 0) sb.append(", ").append(k.same).append(" zaten vardı");
        if (k.unsupported > 0) sb.append(", ").append(k.unsupported).append(" dosya desteklenmiyor");
        if (k.failed > 0) sb.append(", ").append(k.failed).append(" dosya kopyalanamadı");
        sb.append('.');
        return sb.toString();
    }
}
