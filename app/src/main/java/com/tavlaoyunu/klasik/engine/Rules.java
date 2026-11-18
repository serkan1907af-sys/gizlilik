package com.tavlaoyunu.klasik.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Düz tavla kuralları: yasal hamle dizileri üretir. */
public final class Rules {
    private Rules() {}

    /** Verilen noktadan, verilen zarla tek hamle yasal mı? Yasalsa Move döner, değilse null. */
    public static Move single(Board b, int p, int from, int die) {
        if (from == 25) {
            if (b.bar[p] == 0) return null;
        } else {
            if (b.bar[p] > 0) return null; // önce kırık pul girmeli
            if (b.pts[p][from] == 0) return null;
        }
        int to = from - die;
        if (to >= 1) {
            if (b.oppAt(p, to) >= 2) return null;
            return new Move(from, to, die);
        }
        // Toplama
        if (from == 25) return null;
        if (!b.allHome(p)) return null;
        if (to == 0) return new Move(from, 0, die);
        // zar noktadan büyük: sadece daha uzakta pul yoksa
        if (b.farthest(p) <= from) return new Move(from, 0, die);
        return null;
    }

    /** O anki zar için oynanabilecek tüm tek hamleler. */
    public static List<Move> singles(Board b, int p, int die) {
        List<Move> out = new ArrayList<>();
        if (b.bar[p] > 0) {
            Move m = single(b, p, 25, die);
            if (m != null) out.add(m);
            return out;
        }
        for (int from = 24; from >= 1; from--) {
            if (b.pts[p][from] > 0) {
                Move m = single(b, p, from, die);
                if (m != null) out.add(m);
            }
        }
        return out;
    }

    /**
     * Bir atış için tüm yasal tam hamle dizilerini üretir.
     * Kurallar: mümkün olan en çok zar oynanmalı; tek zar oynanabiliyorsa büyük zar oynanmalı.
     */
    public static List<List<Move>> legalSequences(Board start, int p, int d1, int d2) {
        int[] dice;
        if (d1 == d2) dice = new int[]{d1, d1, d1, d1};
        else dice = new int[]{d1, d2};

        List<List<Move>> all = new ArrayList<>();
        boolean[] used = new boolean[dice.length];
        dfs(start, p, dice, used, new ArrayList<Move>(), all);

        int max = 0;
        for (List<Move> s : all) max = Math.max(max, s.size());
        List<List<Move>> best = new ArrayList<>();
        for (List<Move> s : all) if (s.size() == max) best.add(s);

        if (max == 1 && d1 != d2) {
            int big = Math.max(d1, d2);
            // Büyük zarla oynanabiliyorsa sadece onu kabul et
            List<List<Move>> bigOnes = new ArrayList<>();
            for (List<Move> s : best) if (s.get(0).die == big) bigOnes.add(s);
            if (!bigOnes.isEmpty()) best = bigOnes;
        }
        // Aynı sonuca götüren tekrar dizileri ele (sonuç tahtasına göre)
        List<List<Move>> uniq = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (List<Move> s : best) {
            Board t = start.copy();
            for (Move m : s) t.apply(p, m);
            long k = t.key() * 31 + s.size();
            if (seen.add(k)) uniq.add(s);
        }
        return uniq;
    }

    private static void dfs(Board b, int p, int[] dice, boolean[] used, List<Move> cur, List<List<Move>> out) {
        boolean any = false;
        Set<Integer> triedDie = new HashSet<>();
        for (int i = 0; i < dice.length; i++) {
            if (used[i]) continue;
            if (!triedDie.add(dice[i])) continue; // aynı değerdeki zarı tekrar deneme
            List<Move> ms = singles(b, p, dice[i]);
            for (Move m : ms) {
                any = true;
                Board nb = b.copy();
                nb.apply(p, m);
                // 'used' dizisini kopyala; eşit zarlarda ilk kullanılmayanı işaretle
                boolean[] nu = used.clone();
                nu[i] = true;
                cur.add(m);
                dfs(nb, p, dice, nu, cur, out);
                cur.remove(cur.size() - 1);
            }
        }
        if (!any) {
            if (!cur.isEmpty()) out.add(new ArrayList<>(cur));
            else out.add(new ArrayList<Move>());
        }
    }

    /** Başlangıç atışında hiç hamle yoksa boş dizi döner. */
    public static boolean hasMoves(Board b, int p, int d1, int d2) {
        List<List<Move>> s = legalSequences(b, p, d1, d2);
        return !(s.size() == 1 && s.get(0).isEmpty()) && !s.isEmpty();
    }
}
