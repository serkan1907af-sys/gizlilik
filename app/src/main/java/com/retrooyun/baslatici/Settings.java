package com.retrooyun.baslatici;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Kalici ayarlar. Duz bir dosyada tutulur; her seferinde dosyadan okunur ki
 * ana islem ve oyun islemi (":game") ayni degerleri gorsun.
 */
final class Settings {
    private final Properties p = new Properties();
    private final File file;

    private Settings(File f) {
        file = f;
    }

    static Settings load(Context c) {
        Settings s = new Settings(new File(c.getFilesDir(), "settings.properties"));
        if (s.file.isFile()) {
            try (FileInputStream in = new FileInputStream(s.file)) {
                s.p.load(in);
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    synchronized void save() {
        try {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                p.store(os, null);
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        } catch (Exception ignored) {
        }
    }

    // ---- genel ----
    int geti(String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, ""));
        } catch (Exception e) {
            return d;
        }
    }

    boolean getb(String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : v.equals("1");
    }

    String gets(String k, String d) {
        return p.getProperty(k, d);
    }

    void seti(String k, int v) {
        p.setProperty(k, Integer.toString(v));
    }

    void setb(String k, boolean v) {
        p.setProperty(k, v ? "1" : "0");
    }

    void sets(String k, String v) {
        p.setProperty(k, v);
    }

    /** Doom varsayilan: tek cubuk. Quake varsayilan: iki cubuk. */
    boolean twin(String kind) {
        return "twin".equals(gets("scheme." + kind, Actions.quake(kind) ? "twin" : "single"));
    }

    // ---- yerlesim ----
    /** {x orani, y orani, olcek} ya da ayarlanmamissa null. */
    float[] pos(String kind, String id) {
        String v = p.getProperty("pos." + kind + "." + id);
        if (v == null) return null;
        try {
            String[] a = v.split(",");
            return new float[]{Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2])};
        } catch (Exception e) {
            return null;
        }
    }

    void setPos(String kind, String id, float fx, float fy, float scale) {
        p.setProperty("pos." + kind + "." + id, fx + "," + fy + "," + scale);
    }

    boolean visible(String kind, String id) {
        return getb("show." + kind + "." + id, Actions.ctlDefaultVisible(kind, id));
    }

    void setVisible(String kind, String id, boolean v) {
        setb("show." + kind + "." + id, v);
    }

    void clearLayout(String kind) {
        List<String> del = new ArrayList<>();
        for (String k : p.stringPropertyNames())
            if (k.startsWith("pos." + kind + ".") || k.startsWith("show." + kind + ".")) del.add(k);
        for (String k : del) p.remove(k);
    }

    /** Hazir duzenler: "sade", "klasik", "cok". */
    void applyPreset(String kind, String name) {
        clearLayout(kind);
        if (name.equals("sade")) {
            for (String id : Actions.ctlIds(kind)) {
                boolean on = id.equals("stick") || id.equals("fire") || id.equals("use") || id.equals("menu")
                        || id.equals("prev") || id.equals("next") || id.equals("back");
                setVisible(kind, id, on);
            }
        } else if (name.equals("cok")) {
            for (String id : Actions.ctlIds(kind)) setVisible(kind, id, true);
        }
    }

    // ---- kayitli duzenler ----
    private static final String SEP = ";";

    void saveLayout(String kind, String name) {
        StringBuilder sb = new StringBuilder();
        for (String id : Actions.ctlIds(kind)) {
            float[] ps = pos(kind, id);
            if (ps != null) sb.append("pos.").append(kind).append('.').append(id).append('=')
                    .append(ps[0]).append(',').append(ps[1]).append(',').append(ps[2]).append(SEP);
            sb.append("show.").append(kind).append('.').append(id).append('=')
                    .append(visible(kind, id) ? "1" : "0").append(SEP);
        }
        p.setProperty("lay." + kind + "." + name, sb.toString());
    }

    List<String> layoutNames(String kind) {
        TreeSet<String> out = new TreeSet<>();
        String pre = "lay." + kind + ".";
        for (String k : p.stringPropertyNames()) if (k.startsWith(pre)) out.add(k.substring(pre.length()));
        return new ArrayList<>(out);
    }

    void loadLayout(String kind, String name) {
        String v = p.getProperty("lay." + kind + "." + name);
        if (v == null) return;
        clearLayout(kind);
        for (String part : v.split(SEP)) {
            int i = part.indexOf('=');
            if (i > 0) p.setProperty(part.substring(0, i), part.substring(i + 1));
        }
    }

    void deleteLayout(String kind, String name) {
        p.remove("lay." + kind + "." + name);
    }

    // ---- fiziksel tus atamalari ----
    static List<Integer> defaultBinds(String act) {
        List<Integer> l = new ArrayList<>();
        if (act.equals("fire")) l.add(KeyEvent.KEYCODE_VOLUME_UP);
        else if (act.equals("use")) l.add(KeyEvent.KEYCODE_VOLUME_DOWN);
        return l;
    }

    List<Integer> binds(String kind, String act) {
        String v = p.getProperty("bind." + kind + "." + act);
        if (v == null) return defaultBinds(act);
        List<Integer> out = new ArrayList<>();
        for (String s : v.split(",")) {
            try {
                if (!s.trim().isEmpty()) out.add(Integer.parseInt(s.trim()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void putBinds(String kind, String act, List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i));
        }
        p.setProperty("bind." + kind + "." + act, sb.toString());
    }

    /** Verilen tusa atanmis islev (yoksa null). */
    String actionFor(String kind, int keyCode) {
        for (String act : Actions.bindable(kind))
            if (binds(kind, act).contains(keyCode)) return act;
        return null;
    }

    /** Tusu bir isleve atar; baska bir islevden alindiysa onun adini dondurur. */
    String bindKey(String kind, String act, int keyCode) {
        String taken = null;
        for (String other : Actions.bindable(kind)) {
            List<Integer> l = new ArrayList<>(binds(kind, other));
            if (other.equals(act)) {
                if (!l.contains(keyCode)) l.add(keyCode);
            } else if (l.remove(Integer.valueOf(keyCode))) {
                taken = other;
            }
            putBinds(kind, other, l);
        }
        return taken;
    }

    void clearBind(String kind, String act) {
        putBinds(kind, act, new ArrayList<Integer>());
    }

    void resetBinds(String kind) {
        List<String> del = new ArrayList<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith("bind." + kind + ".")) del.add(k);
        for (String k : del) p.remove(k);
    }

    String bindText(String kind, String act) {
        List<Integer> l = binds(kind, act);
        if (l.isEmpty()) return "atanmamış";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(keyName(l.get(i)));
        }
        return sb.toString();
    }

    static String keyName(int c) {
        switch (c) {
            case KeyEvent.KEYCODE_VOLUME_UP: return "Ses +";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "Ses −";
            default:
                String n = KeyEvent.keyCodeToString(c);
                if (n.startsWith("KEYCODE_")) n = n.substring(8);
                return n.replace("BUTTON_", "Düğme ").replace('_', ' ');
        }
    }

    /**
     * Ses tuslarini telefonun durusuna gore normallestirir: "Ses +" her zaman sag eldeki tus olur.
     * Telefon yatayken (ROTATION_90) ust kenar sola gectigi icin tuslar yer degistirir.
     */
    @SuppressWarnings("deprecation")
    static int normalize(Activity a, Settings s, int code) {
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return code;
        boolean swap = false;
        if (s.getb("vol.auto", true)) {
            int rot = a.getWindowManager().getDefaultDisplay().getRotation();
            if (rot == Surface.ROTATION_90) swap = true;
        }
        if (s.getb("vol.flip", false)) swap = !swap;
        if (!swap) return code;
        return code == KeyEvent.KEYCODE_VOLUME_UP ? KeyEvent.KEYCODE_VOLUME_DOWN : KeyEvent.KEYCODE_VOLUME_UP;
    }
}
