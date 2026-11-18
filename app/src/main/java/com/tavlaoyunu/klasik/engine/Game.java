package com.tavlaoyunu.klasik.engine;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Bir maçın akışı. Zarlar SecureRandom ile atılır; animasyon veya yapay zeka zarı ASLA belirlemez.
 * Oyuncu 0 = insan (beyaz), oyuncu 1 = bilgisayar (siyah).
 */
public final class Game {
    public static final int HUMAN = 0;
    public static final int CPU = 1;

    private final SecureRandom dice = new SecureRandom();

    public Board board = Board.initial();
    public int turn = HUMAN;
    public int d1, d2;
    public boolean rolled = false;
    public boolean over = false;
    public int winner = -1;
    public int winType = 0; // 1 = mars

    /** Bu turun kalan yasal dizileri (insan için, oynanan hamlelere göre süzülür). */
    private List<List<Move>> sequences = new ArrayList<>();
    private final List<Move> played = new ArrayList<>();
    private final List<Board> undoStack = new ArrayList<>();
    private Board turnStart;

    // ---- Zar istatistiği (oyun sonu "adil zar" ekranı için) ----
    public final int[][] faceCount = new int[2][7];
    public final int[] doubles = new int[2];
    public final int[] rollCount = new int[2];
    public final long[] pipRolled = new long[2];

    public Game() {
    }

    /** Açılış: iki taraf birer zar atar, büyük atan o atışla başlar. Başlangıç atışını ayarlar. */
    public void start() {
        board = Board.initial();
        over = false;
        winner = -1;
        winType = 0;
        int a, b;
        do {
            a = dice.nextInt(6) + 1;
            b = dice.nextInt(6) + 1;
        } while (a == b);
        turn = a > b ? HUMAN : CPU;
        applyRoll(turn, a, b);
    }

    private int nextDie() {
        return dice.nextInt(6) + 1;
    }

    /** Sıradaki oyuncu için zar at. */
    public void roll() {
        if (over || rolled) return;
        applyRoll(turn, nextDie(), nextDie());
    }

    private void applyRoll(int p, int a, int b) {
        d1 = a;
        d2 = b;
        rolled = true;
        faceCount[p][a]++;
        faceCount[p][b]++;
        rollCount[p]++;
        pipRolled[p] += (a == b) ? a * 4 : a + b;
        if (a == b) doubles[p]++;
        played.clear();
        undoStack.clear();
        turnStart = board.copy();
        sequences = Rules.legalSequences(board, p, a, b);
        if (sequences.size() == 1 && sequences.get(0).isEmpty()) {
            sequences = new ArrayList<>();
        }
    }

    /** Hamle yok mu (pas)? */
    public boolean noMoves() {
        return rolled && sequences.isEmpty();
    }

    /** İnsanın şu an seçebileceği tek hamleler. */
    public List<Move> nextMoves() {
        List<Move> out = new ArrayList<>();
        if (!rolled || over) return out;
        int n = played.size();
        for (List<Move> s : sequences) {
            if (s.size() <= n) continue;
            boolean prefix = true;
            for (int i = 0; i < n; i++) {
                if (!s.get(i).equals(played.get(i))) {
                    prefix = false;
                    break;
                }
            }
            if (!prefix) continue;
            Move m = s.get(n);
            if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    /** Bir tek hamleyi uygular (insan veya bilgisayar). Vurma varsa true. */
    public boolean play(Move m) {
        undoStack.add(board.copy());
        boolean hit = board.apply(turn, m);
        m.hit = hit;
        played.add(m);
        if (board.won(turn)) {
            over = true;
            winner = turn;
            winType = board.winType(turn);
        }
        return hit;
    }

    /** Tur bitti mi (tüm yasal zarlar kullanıldı)? */
    public boolean turnComplete() {
        if (over) return true;
        if (!rolled) return false;
        if (sequences.isEmpty()) return true;
        int n = played.size();
        for (List<Move> s : sequences) {
            if (s.size() > n) {
                boolean prefix = true;
                for (int i = 0; i < n; i++) {
                    if (!s.get(i).equals(played.get(i))) {
                        prefix = false;
                        break;
                    }
                }
                if (prefix) return false;
            }
        }
        return true;
    }

    /** İnsan için geri al (sadece bu tur içinde). */
    public boolean canUndo() {
        return turn == HUMAN && rolled && !over && !played.isEmpty();
    }

    public void undo() {
        if (!canUndo()) return;
        board = undoStack.remove(undoStack.size() - 1);
        played.remove(played.size() - 1);
    }

    /** Turu bitir, sırayı değiştir. */
    public void endTurn() {
        if (over) return;
        turn = 1 - turn;
        rolled = false;
        played.clear();
        undoStack.clear();
        sequences = new ArrayList<>();
    }

    /** İpucu: insan için en iyi sıradaki hamle (yapay zeka 'zor' ile). */
    public Move hint() {
        if (turn != HUMAN || !rolled || sequences.isEmpty()) return null;
        List<List<Move>> remaining = new ArrayList<>();
        int n = played.size();
        for (List<Move> s : sequences) {
            if (s.size() <= n) continue;
            boolean prefix = true;
            for (int i = 0; i < n; i++) {
                if (!s.get(i).equals(played.get(i))) {
                    prefix = false;
                    break;
                }
            }
            if (prefix) remaining.add(s);
        }
        if (remaining.isEmpty()) return null;
        Board base = turnStart;
        Ai ai = new Ai(3, 12345L);
        List<List<Move>> full = new ArrayList<>();
        for (List<Move> s : remaining) full.add(s);
        List<Move> pick = ai.choose(base, HUMAN, full);
        if (pick.size() > n) return pick.get(n);
        return remaining.get(0).get(n);
    }

    /** Bilgisayarın turunu (hamle dizisini) hesapla. Tahtayı değiştirmez. */
    public List<Move> cpuPlan(int level) {
        Ai ai = new Ai(level, System.nanoTime());
        return ai.choose(board, CPU, sequences);
    }

    public List<Move> playedMoves() {
        return played;
    }

    public Board turnStartBoard() {
        return turnStart;
    }

    public int pipHuman() {
        return board.pip(HUMAN);
    }

    public int pipCpu() {
        return board.pip(CPU);
    }
}
