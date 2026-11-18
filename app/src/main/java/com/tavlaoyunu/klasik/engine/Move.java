package com.tavlaoyunu.klasik.engine;

/** Tek bir pul hareketi: from (1..24 veya 25=kırık), to (0 = dışarı), die = kullanılan zar. */
public final class Move {
    public final int from;
    public final int to;
    public final int die;
    public boolean hit;

    public Move(int from, int to, int die) {
        this.from = from;
        this.to = to;
        this.die = die;
    }

    @Override
    public String toString() {
        return (from == 25 ? "bar" : String.valueOf(from)) + "/" + (to <= 0 ? "off" : String.valueOf(to));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Move)) return false;
        Move m = (Move) o;
        return m.from == from && m.to == to && m.die == die;
    }

    @Override
    public int hashCode() {
        return from * 1000 + to * 10 + die;
    }
}
