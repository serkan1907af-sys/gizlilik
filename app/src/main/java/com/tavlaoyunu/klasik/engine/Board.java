package com.tavlaoyunu.klasik.engine;

import java.util.Arrays;

/**
 * Düz tavla tahtası.
 * Her oyuncu için kendi bakış açısından numaralandırma kullanılır:
 *   24 = en uzak nokta, 1 = evin son noktası, 25 = kırık (bar), 0 = dışarı (toplanan).
 * Oyuncu 0 = beyaz (insan), oyuncu 1 = siyah (bilgisayar).
 * Rakibin 'i' noktasındaki pulu bizim bakışımızdan (25 - i) noktasındadır.
 */
public final class Board {
    public final int[][] pts = new int[2][26]; // pts[p][1..24]
    public final int[] bar = new int[2];
    public final int[] off = new int[2];

    public static Board initial() {
        Board b = new Board();
        for (int p = 0; p < 2; p++) {
            b.pts[p][24] = 2;
            b.pts[p][13] = 5;
            b.pts[p][8] = 3;
            b.pts[p][6] = 5;
        }
        return b;
    }

    public Board copy() {
        Board b = new Board();
        for (int p = 0; p < 2; p++) {
            System.arraycopy(pts[p], 0, b.pts[p], 0, 26);
            b.bar[p] = bar[p];
            b.off[p] = off[p];
        }
        return b;
    }

    /** Rakibin, bizim 'point' noktamızdaki pul sayısı. */
    public int oppAt(int p, int point) {
        return pts[1 - p][25 - point];
    }

    public int count(int p, int point) {
        return pts[p][point];
    }

    public boolean allHome(int p) {
        if (bar[p] > 0) return false;
        for (int i = 7; i <= 24; i++) if (pts[p][i] > 0) return false;
        return true;
    }

    public int pip(int p) {
        int s = bar[p] * 25;
        for (int i = 1; i <= 24; i++) s += pts[p][i] * i;
        return s;
    }

    public boolean won(int p) {
        return off[p] == 15;
    }

    /** En uzaktaki pulun noktası (bar varsa 25), yoksa 0. */
    public int farthest(int p) {
        if (bar[p] > 0) return 25;
        for (int i = 24; i >= 1; i--) if (pts[p][i] > 0) return i;
        return 0;
    }

    /** Tek bir hamle uygular. Vurma varsa true döner. */
    public boolean apply(int p, Move m) {
        boolean hit = false;
        if (m.from == 25) bar[p]--;
        else pts[p][m.from]--;
        if (m.to <= 0) {
            off[p]++;
        } else {
            pts[p][m.to]++;
            int opp = 1 - p;
            int oppPoint = 25 - m.to;
            if (pts[opp][oppPoint] == 1) {
                pts[opp][oppPoint] = 0;
                bar[opp]++;
                hit = true;
            }
        }
        return hit;
    }

    public int totalCheckers(int p) {
        int s = bar[p] + off[p];
        for (int i = 1; i <= 24; i++) s += pts[p][i];
        return s;
    }

    /** Mars türü: 0 = normal, 1 = mars (gammon). Kazanan p. */
    public int winType(int p) {
        int opp = 1 - p;
        if (off[opp] > 0) return 0;
        return 1;
    }

    public long key() {
        return Arrays.hashCode(pts[0]) * 31L * 31L + Arrays.hashCode(pts[1]) * 31L
                + bar[0] * 7 + bar[1] * 13 + off[0] * 17 + off[1] * 19;
    }
}
