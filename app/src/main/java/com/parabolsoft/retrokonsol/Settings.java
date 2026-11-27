package com.parabolsoft.retrokonsol;

import android.content.Context;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

    long getl(String k, long d) {
        try {
            return Long.parseLong(p.getProperty(k, ""));
        } catch (Exception e) {
            return d;
        }
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

    void setl(String k, long v) {
        p.setProperty(k, Long.toString(v));
    }

    // ---- ekran tuslari yerlesimi (dikey / yatay ayri) ----
    private static String pk(Sys s, String ori, String id) {
        return "pos." + s.id + "." + ori + "." + id;
    }

    private static String vk(Sys s, String ori, String id) {
        return "show." + s.id + "." + ori + "." + id;
    }

    /** {x orani, y orani, olcek} ya da ayarlanmamissa null. */
    float[] pos(Sys s, String ori, String id) {
        String v = p.getProperty(pk(s, ori, id));
        if (v == null) return null;
        try {
            String[] a = v.split(",");
            return new float[]{Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2])};
        } catch (Exception e) {
            return null;
        }
    }

    void setPos(Sys s, String ori, String id, float fx, float fy, float scale) {
        p.setProperty(pk(s, ori, id), fx + "," + fy + "," + scale);
    }

    boolean visible(Sys s, String ori, String id) {
        return getb(vk(s, ori, id), true);
    }

    void setVisible(Sys s, String ori, String id, boolean v) {
        setb(vk(s, ori, id), v);
    }

    void clearLayout(Sys s, String ori) {
        List<String> del = new ArrayList<>();
        String a = "pos." + s.id + "." + ori + ".", b = "show." + s.id + "." + ori + ".";
        for (String k : p.stringPropertyNames()) if (k.startsWith(a) || k.startsWith(b)) del.add(k);
        for (String k : del) p.remove(k);
    }

    // ---- fiziksel tus atamalari ----
    List<Integer> binds(Sys s, String act) {
        List<Integer> out = new ArrayList<>();
        String v = p.getProperty("bind." + s.id + "." + act);
        if (v == null) return out;
        for (String x : v.split(",")) {
            try {
                if (!x.trim().isEmpty()) out.add(Integer.parseInt(x.trim()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void putBinds(Sys s, String act, List<Integer> l) {
        if (l.isEmpty()) {
            p.remove("bind." + s.id + "." + act);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i));
        }
        p.setProperty("bind." + s.id + "." + act, sb.toString());
    }

    /** Verilen tusa atanmis islev (yoksa null). */
    String actionFor(Sys s, int keyCode) {
        for (String act : Actions.bindable(s))
            if (binds(s, act).contains(keyCode)) return act;
        return null;
    }

    /** Tusu bir isleve atar; baska bir islevden alindiysa onun adini dondurur. */
    String bindKey(Sys s, String act, int keyCode) {
        String taken = null;
        for (String other : Actions.bindable(s)) {
            List<Integer> l = new ArrayList<>(binds(s, other));
            if (other.equals(act)) {
                if (!l.contains(keyCode)) l.add(keyCode);
            } else if (l.remove(Integer.valueOf(keyCode))) {
                taken = other;
            }
            putBinds(s, other, l);
        }
        return taken;
    }

    void clearBind(Sys s, String act) {
        p.remove("bind." + s.id + "." + act);
    }

    void resetBinds(Sys s) {
        List<String> del = new ArrayList<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith("bind." + s.id + ".")) del.add(k);
        for (String k : del) p.remove(k);
    }

    String bindText(Sys s, String act) {
        List<Integer> l = binds(s, act);
        if (l.isEmpty()) return "varsayılan";
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
}
