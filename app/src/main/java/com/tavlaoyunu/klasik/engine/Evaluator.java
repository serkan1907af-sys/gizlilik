package com.tavlaoyunu.klasik.engine;

/** Konum değerlendirme (p oyuncusu açısından, büyük = iyi). */
public final class Evaluator {
    private Evaluator() {}

    // Mesafe d (1..12) için 36 atıştan kaçı vurur (engelleri yok sayan yaklaşık değer)
    private static final int[] HITS = {0, 11, 12, 14, 15, 15, 16, 6, 6, 5, 3, 2, 3};

    public static boolean contact(Board b, int p) {
        int mine = b.farthest(p);
        int opp = b.farthest(1 - p);
        return mine + opp > 25;
    }

    public static double evaluate(Board b, int p) {
        int o = 1 - p;
        if (b.off[p] == 15) return 100000;
        if (b.off[o] == 15) return -100000;

        double score = (b.pip(o) - b.pip(p)) * 1.0;
        score += (b.off[p] - b.off[o]) * 4.0;

        if (!contact(b, p)) {
            return score;
        }

        score += b.bar[o] * 22.0;
        score -= b.bar[p] * 28.0;

        // Taşlar ve boşluklar
        int run = 0, bestRun = 0;
        for (int i = 1; i <= 24; i++) {
            int c = b.pts[p][i];
            if (c >= 2) {
                double w = (i <= 6) ? 5.0 : (i <= 12 ? 3.0 : 1.5);
                if (i == 7) w = 4.5;
                if (i == 20) w = 2.5; // rakibin bar noktası
                score += w;
                if (c > 4) score -= (c - 4) * 1.5; // fazla yığılma
                run++;
                bestRun = Math.max(bestRun, run);
            } else {
                run = 0;
            }
            if (c == 1) {
                score -= blotRisk(b, p, i);
            }
        }
        score += bestRun * bestRun * 1.2;

        // Rakibin tek bıraktığı taşlara vurma şansı (bizim sırada)
        for (int i = 1; i <= 24; i++) {
            if (b.pts[o][i] == 1) {
                score += 0.35 * shotValue(b, p, 25 - i);
            }
        }
        return score;
    }

    /** Benim 'point' (bizim bakış) noktamda tek taş: rakip vurma ihtimali * kayıp. */
    private static double blotRisk(Board b, int p, int point) {
        int o = 1 - p;
        double hit = hitProbability(b, o, 25 - point);
        // Vurulursa kaybettiğimiz mesafe: taşın bizim yolumuzdaki ilerlemesi
        double loss = (25 - point) * 0.9 + 6.0;
        return hit * loss * 0.85;
    }

    private static double shotValue(Board b, int shooter, int targetInShooterPoint) {
        return hitProbability(b, shooter, targetInShooterPoint) * 20.0;
    }

    /** Vuran oyuncunun 'target' noktasındaki rakip taşına vurma olasılığı [0..1]. */
    private static double hitProbability(Board b, int shooter, int target) {
        double sum = 0;
        if (b.bar[shooter] > 0) {
            int d = 25 - target;
            if (d >= 1 && d <= 12) sum += HITS[d] / 36.0;
            return Math.min(1.0, sum);
        }
        for (int j = target + 1; j <= 24; j++) {
            int c = b.pts[shooter][j];
            if (c == 0) continue;
            int d = j - target;
            if (d <= 12) sum += HITS[d] / 36.0 * Math.min(c, 2) * 0.6;
        }
        return Math.min(1.0, sum);
    }
}
