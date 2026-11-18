package com.tavlaoyunu.klasik.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Bilgisayar oyuncusu. Zarları ASLA etkilemez; sadece yasal hamleler arasından seçim yapar.
 * Seviye: 1 = kolay, 2 = orta, 3 = zor.
 */
public final class Ai {
    private final Random rnd;
    private final int level;

    public Ai(int level, long seed) {
        this.level = level;
        this.rnd = new Random(seed);
    }

    public List<Move> choose(Board b, int p, List<List<Move>> options) {
        if (options.isEmpty()) return new ArrayList<>();
        if (options.size() == 1) return options.get(0);

        if (level <= 1) return chooseEasy(b, p, options);
        if (level == 2) return chooseScored(b, p, options, 7.0);
        return chooseHard(b, p, options);
    }

    private List<Move> chooseEasy(Board b, int p, List<List<Move>> options) {
        // Büyük gürültü: çoğu zaman rastgele, ara sıra iyi hamle
        if (rnd.nextInt(100) < 45) return chooseScored(b, p, options, 25.0);
        return options.get(rnd.nextInt(options.size()));
    }

    private List<Move> chooseScored(Board b, int p, List<List<Move>> options, double noise) {
        double best = -1e18;
        List<Move> pick = options.get(0);
        for (List<Move> s : options) {
            Board t = b.copy();
            for (Move m : s) t.apply(p, m);
            double v = Evaluator.evaluate(t, p) + (rnd.nextDouble() - 0.5) * 2 * noise;
            if (v > best) {
                best = v;
                pick = s;
            }
        }
        return pick;
    }

    private static final class Scored {
        final List<Move> seq;
        final Board after;
        double v;

        Scored(List<Move> seq, Board after, double v) {
            this.seq = seq;
            this.after = after;
            this.v = v;
        }
    }

    private List<Move> chooseHard(Board b, int p, List<List<Move>> options) {
        List<Scored> list = new ArrayList<>();
        for (List<Move> s : options) {
            Board t = b.copy();
            for (Move m : s) t.apply(p, m);
            list.add(new Scored(s, t, Evaluator.evaluate(t, p) + (rnd.nextDouble() - 0.5) * 0.4));
        }
        Collections.sort(list, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored c) {
                return Double.compare(c.v, a.v);
            }
        });
        int k = Math.min(6, list.size());
        int o = 1 - p;
        double best = -1e18;
        List<Move> pick = list.get(0).seq;
        for (int i = 0; i < k; i++) {
            Scored sc = list.get(i);
            if (sc.after.off[p] == 15) return sc.seq;
            double total = 0;
            for (int d1 = 1; d1 <= 6; d1++) {
                for (int d2 = d1; d2 <= 6; d2++) {
                    double w = (d1 == d2) ? 1.0 : 2.0;
                    List<List<Move>> replies = Rules.legalSequences(sc.after, o, d1, d2);
                    double worst = 1e18; // rakibin en iyi cevabı bizim için en kötüsü
                    for (List<Move> r : replies) {
                        Board t = sc.after.copy();
                        for (Move m : r) t.apply(o, m);
                        double val = Evaluator.evaluate(t, p);
                        if (val < worst) worst = val;
                    }
                    if (worst > 1e17) worst = Evaluator.evaluate(sc.after, p);
                    total += w * worst;
                }
            }
            double v = total / 36.0 + sc.v * 0.15;
            if (v > best) {
                best = v;
                pick = sc.seq;
            }
        }
        return pick;
    }
}
