package com.tavlaoyunu.klasik;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Jeton, seviye, günlük ödül, görevler ve koleksiyon (zar/taş/tahta/avatar). Hepsi telefonda saklanır. */
public final class Economy {
    private Economy() {}

    public static final int DICE = 0, CHECKER = 1, BOARD = 2, AVATAR = 3;
    public static final int COMMON = 0, RARE = 1, EPIC = 2, LEGEND = 3;

    public static String T(String tr, String en) {
        return "tr".equals(Locale.getDefault().getLanguage()) ? tr : en;
    }

    // ------------------------------------------------------------------ eşyalar

    public static final class Item {
        public final String id;
        public final int kind;
        public final String tr, en;
        public final int price, rarity;
        public final int[] c;
        public final String emoji;
        public final int boardId;

        Item(String id, int kind, String tr, String en, int price, int rarity, int[] c, String emoji, int boardId) {
            this.id = id;
            this.kind = kind;
            this.tr = tr;
            this.en = en;
            this.price = price;
            this.rarity = rarity;
            this.c = c;
            this.emoji = emoji;
            this.boardId = boardId;
        }

        public String name() {
            return T(tr, en);
        }
    }

    private static final List<Item> ALL = new ArrayList<>();

    private static void add(String id, int kind, String tr, String en, int price, int rarity, int[] c, String emoji, int boardId) {
        ALL.add(new Item(id, kind, tr, en, price, rarity, c, emoji, boardId));
    }

    static {
        // zarlar: {üst renk, alt renk, nokta rengi, amblem(0 yok,1 taç)}
        add("d0", DICE, "Klasik", "Classic", 0, COMMON, new int[]{0xFFFFFFFF, 0xFFD9D2C3, 0xFF050505, 0}, null, 0);
        add("d1", DICE, "Altın", "Gold", 300, RARE, new int[]{0xFFFFE680, 0xFFC9962B, 0xFF3A2400, 0}, null, 0);
        add("d3", DICE, "Gece", "Night", 250, COMMON, new int[]{0xFF4A4A52, 0xFF121216, 0xFFFFFFFF, 0}, null, 0);
        add("d5", DICE, "Buz", "Ice", 450, RARE, new int[]{0xFFE8F7FF, 0xFF8CCBF0, 0xFF0B3D91, 0}, null, 0);
        add("d4", DICE, "Yakut", "Ruby", 600, EPIC, new int[]{0xFFFF7B7B, 0xFFB01030, 0xFFFFFFFF, 0}, null, 0);
        add("d2", DICE, "Neon", "Neon", 700, EPIC, new int[]{0xFF2B2B3A, 0xFF0A0A12, 0xFF39FF88, 0}, null, 0);
        add("d6", DICE, "Taçlı", "Crowned", 1000, LEGEND, new int[]{0xFFFFFFFF, 0xFFE6DDC8, 0xFF1565C0, 1}, null, 0);
        // taşlar: {insan dolgu, insan kenar, bilgisayar dolgu, bilgisayar kenar, amblem(0,1 taç,2 yıldız)}; 0 = tahta rengi
        add("c0", CHECKER, "Klasik", "Classic", 0, COMMON, new int[]{0, 0, 0, 0, 0}, null, 0);
        add("c1", CHECKER, "Taç", "Crown", 300, RARE, new int[]{0, 0, 0, 0, 1}, null, 0);
        add("c2", CHECKER, "Yıldız", "Star", 300, RARE, new int[]{0, 0, 0, 0, 2}, null, 0);
        add("c3", CHECKER, "Gümüş-Altın", "Silver-Gold", 500, EPIC, new int[]{0xFFE9ECF0, 0xFF9AA0A8, 0xFFE8B923, 0xFF8A6508, 0}, null, 0);
        add("c4", CHECKER, "Kırmızı-Mavi", "Red-Blue", 500, RARE, new int[]{0xFFE53935, 0xFF8E1B1B, 0xFF1E88E5, 0xFF0D47A1, 0}, null, 0);
        add("c5", CHECKER, "Zümrüt-Yakut", "Emerald-Ruby", 900, LEGEND, new int[]{0xFF2ECC71, 0xFF0B6B35, 0xFFD81B60, 0xFF7A0A32, 1}, null, 0);
        // tahtalar
        add("b0", BOARD, "Yeşil Çuha", "Green Felt", 0, COMMON, null, null, 0);
        add("b2", BOARD, "Klasik Ahşap", "Classic Wood", 0, COMMON, null, null, 2);
        add("b1", BOARD, "Koyu", "Dark", 400, RARE, null, null, 1);
        add("b3", BOARD, "Mavi", "Blue", 500, RARE, null, null, 3);
        add("b5", BOARD, "Mor Gece", "Purple Night", 700, EPIC, null, null, 5);
        add("b4", BOARD, "Siyah Altın", "Black Gold", 900, LEGEND, null, null, 4);
        // avatarlar
        add("a0", AVATAR, "Gülen", "Smiley", 0, COMMON, null, "🙂", 0);
        add("a1", AVATAR, "Havalı", "Cool", 0, COMMON, null, "😎", 0);
        add("a2", AVATAR, "Kovboy", "Cowboy", 150, COMMON, null, "🤠", 0);
        add("a3", AVATAR, "Tilki", "Fox", 200, COMMON, null, "🦊", 0);
        add("a4", AVATAR, "Kedi", "Cat", 200, COMMON, null, "🐱", 0);
        add("a5", AVATAR, "Kaplan", "Tiger", 300, RARE, null, "🐯", 0);
        add("a9", AVATAR, "Robot", "Robot", 300, RARE, null, "🤖", 0);
        add("a6", AVATAR, "Aslan", "Lion", 400, RARE, null, "🦁", 0);
        add("a10", AVATAR, "Kartal", "Eagle", 400, RARE, null, "🦅", 0);
        add("a7", AVATAR, "Ejderha", "Dragon", 500, EPIC, null, "🐉", 0);
        add("a11", AVATAR, "Kurt", "Wolf", 500, EPIC, null, "🐺", 0);
        add("a8", AVATAR, "Kral", "King", 800, LEGEND, null, "👑", 0);
    }

    public static List<Item> items(int kind) {
        List<Item> out = new ArrayList<>();
        for (Item i : ALL) if (i.kind == kind) out.add(i);
        return out;
    }

    public static Item find(String id) {
        for (Item i : ALL) if (i.id.equals(id)) return i;
        return ALL.get(0);
    }

    public static boolean owns(String id) {
        Item it = find(id);
        if (it.price == 0 || Prefs.pro()) return true;
        return ("," + Prefs.gets("owned", "") + ",").contains("," + id + ",");
    }

    private static String defaultId(int kind) {
        switch (kind) {
            case DICE: return "d0";
            case CHECKER: return "c0";
            case AVATAR: return "a0";
            default: return "b0";
        }
    }

    public static Item equipped(int kind) {
        String id;
        if (kind == BOARD) {
            id = "b" + Prefs.theme();
        } else {
            id = Prefs.gets("eq" + kind, defaultId(kind));
        }
        Item it = find(id);
        if (it.kind != kind || !owns(it.id)) it = find(defaultId(kind));
        return it;
    }

    public static void equip(Item it) {
        if (it.kind == BOARD) Prefs.setTheme(it.boardId);
        else Prefs.puts("eq" + it.kind, it.id);
    }

    /** 0 = kuşandı/aldı, 1 = jeton yetmedi. */
    public static int buyOrEquip(Item it) {
        if (owns(it.id)) {
            equip(it);
            return 0;
        }
        if (!spend(it.price)) return 1;
        String o = Prefs.gets("owned", "");
        Prefs.puts("owned", o.isEmpty() ? it.id : o + "," + it.id);
        equip(it);
        return 0;
    }

    // ------------------------------------------------------------------ jeton / oyuncu

    public static int coins() { return Prefs.geti("coins", 100); }

    public static void addCoins(int n) { Prefs.puti("coins", Math.max(0, coins() + n)); }

    public static boolean spend(int n) {
        int c = coins();
        if (c < n) return false;
        Prefs.puti("coins", c - n);
        return true;
    }

    public static int mult() { return Prefs.pro() ? 2 : 1; }

    public static String name() { return Prefs.gets("pname", T("Sen", "You")); }

    public static void setName(String n) {
        String t = n == null ? "" : n.trim();
        if (t.length() > 14) t = t.substring(0, 14);
        if (!t.isEmpty()) Prefs.puts("pname", t);
    }

    public static int xp() { return Prefs.geti("xp", 0); }

    private static int need(int level) { return 100 + 50 * (level - 1); }

    public static int level() {
        int l = 1, x = xp();
        while (x >= need(l)) {
            x -= need(l);
            l++;
        }
        return l;
    }

    /** {şu seviyede kazanılan, seviye için gereken} */
    public static int[] levelProgress() {
        int l = 1, x = xp();
        while (x >= need(l)) {
            x -= need(l);
            l++;
        }
        return new int[]{x, need(l)};
    }

    // ------------------------------------------------------------------ günlük ödül

    public static final int[] DAILY = {20, 30, 40, 50, 60, 80, 150};

    private static long today() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    private static long lastDay() {
        try {
            return Long.parseLong(Prefs.gets("daily_day", "0"));
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean canClaimDaily() { return lastDay() != today(); }

    /** Bugün (alınacak ya da alınmış) günün sıra numarası 0..6. */
    public static int dailyIndex() {
        long t = today(), l = lastDay();
        int last = Prefs.geti("daily_idx", -1);
        if (l == t) return Math.max(0, last);
        if (l == t - 1 && last >= 0) return (last + 1) % 7;
        return 0;
    }

    public static int claimDaily() {
        if (!canClaimDaily()) return 0;
        int idx = dailyIndex();
        int amount = DAILY[idx] * mult();
        addCoins(amount);
        Prefs.puts("daily_day", Long.toString(today()));
        Prefs.puti("daily_idx", idx);
        return amount;
    }

    // ------------------------------------------------------------------ görevler

    public static final class Mission {
        public final String tr, en;
        public final int type, target, reward;

        Mission(String tr, String en, int type, int target, int reward) {
            this.tr = tr;
            this.en = en;
            this.type = type;
            this.target = target;
            this.reward = reward;
        }

        public String text() { return T(tr, en); }
    }

    private static final Mission[] POOL = {
            new Mission("1 oyun kazan", "Win 1 game", 0, 1, 40),
            new Mission("2 oyun kazan", "Win 2 games", 0, 2, 80),
            new Mission("3 taş kır", "Hit 3 checkers", 1, 3, 30),
            new Mission("8 taş kır", "Hit 8 checkers", 1, 8, 70),
            new Mission("2 kez çift at", "Roll doubles 2 times", 2, 2, 30),
            new Mission("3 oyun oyna", "Play 3 games", 3, 3, 40),
            new Mission("Zor seviyede 1 oyun kazan", "Win 1 game on Hard", 4, 1, 100),
            new Mission("Mars yaparak kazan", "Win with a gammon", 5, 1, 100)
    };

    private static final int[] OFFS = {0, 3, 5};

    private static void ensureDay() {
        String d = Long.toString(today());
        if (!d.equals(Prefs.gets("m_day", ""))) {
            Prefs.puts("m_day", d);
            for (int i = 0; i < 3; i++) {
                Prefs.puti("m_p" + i, 0);
                Prefs.puti("m_c" + i, 0);
            }
            Prefs.puti("ad_n", 0);
        }
    }

    public static Mission mission(int i) {
        return POOL[(int) ((today() + OFFS[i]) % POOL.length)];
    }

    public static int missionProgress(int i) {
        ensureDay();
        return Math.min(mission(i).target, Prefs.geti("m_p" + i, 0));
    }

    public static boolean missionDone(int i) { return missionProgress(i) >= mission(i).target; }

    public static boolean missionClaimed(int i) {
        ensureDay();
        return Prefs.geti("m_c" + i, 0) == 1;
    }

    public static int claimMission(int i) {
        ensureDay();
        if (!missionDone(i) || missionClaimed(i)) return 0;
        int amount = mission(i).reward * mult();
        addCoins(amount);
        Prefs.puti("m_c" + i, 1);
        return amount;
    }

    public static boolean anythingToClaim() {
        if (canClaimDaily()) return true;
        for (int i = 0; i < 3; i++) if (missionDone(i) && !missionClaimed(i)) return true;
        return false;
    }

    // ------------------------------------------------------------------ reklamla jeton

    public static final int AD_LIMIT = 5, AD_COINS = 25;

    public static int adsLeft() {
        ensureDay();
        return Math.max(0, AD_LIMIT - Prefs.geti("ad_n", 0));
    }

    public static void adWatched() {
        ensureDay();
        Prefs.puti("ad_n", Prefs.geti("ad_n", 0) + 1);
        addCoins(AD_COINS);
    }

    // ------------------------------------------------------------------ oyun sonu

    public static final class Reward {
        public int coins, xp, newLevel;
        public boolean levelUp;
    }

    public static Reward onGameFinished(int level, boolean won, boolean gammon, int hits, int doubles) {
        ensureDay();
        int[] win = {0, 20, 35, 60};
        int lv = Math.max(1, Math.min(3, level));
        Reward r = new Reward();
        int base = won ? win[lv] : 5;
        if (won && gammon) base = base * 3 / 2;
        r.coins = base * mult();
        r.xp = won ? 30 + 10 * lv : 10;
        int before = level();
        Prefs.puti("xp", xp() + r.xp);
        int after = level();
        if (after > before) {
            r.levelUp = true;
            r.newLevel = after;
            r.coins += 50;
        }
        addCoins(r.coins);
        for (int i = 0; i < 3; i++) {
            Mission m = mission(i);
            int add = 0;
            switch (m.type) {
                case 0: add = won ? 1 : 0; break;
                case 1: add = hits; break;
                case 2: add = doubles; break;
                case 3: add = 1; break;
                case 4: add = (won && level == 3) ? 1 : 0; break;
                case 5: add = (won && gammon) ? 1 : 0; break;
                default: break;
            }
            if (add > 0) Prefs.puti("m_p" + i, Prefs.geti("m_p" + i, 0) + add);
        }
        return r;
    }
}
