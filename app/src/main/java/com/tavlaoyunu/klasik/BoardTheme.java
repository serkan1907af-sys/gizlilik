package com.tavlaoyunu.klasik;

/** Tahta renk takımları. 0 ücretsiz, diğerleri Pro. */
public final class BoardTheme {
    public final int id;
    public final int nameRes;
    public final boolean pro;
    public final int bg, frame, board, pointA, pointB, bar, white, whiteEdge, black, blackEdge, accent, text;

    private BoardTheme(int id, int nameRes, boolean pro, int bg, int frame, int board, int pointA, int pointB,
                       int bar, int white, int whiteEdge, int black, int blackEdge, int accent, int text) {
        this.id = id;
        this.nameRes = nameRes;
        this.pro = pro;
        this.bg = bg;
        this.frame = frame;
        this.board = board;
        this.pointA = pointA;
        this.pointB = pointB;
        this.bar = bar;
        this.white = white;
        this.whiteEdge = whiteEdge;
        this.black = black;
        this.blackEdge = blackEdge;
        this.accent = accent;
        this.text = text;
    }

    public static final int COUNT = 5;

    public static BoardTheme get(int id) {
        switch (id) {
            case 1:
                return new BoardTheme(1, R.string.theme_dark, true, 0xFF0E0E12, 0xFF1E1E24, 0xFF2B2D36, 0xFF4A5068, 0xFF8A90A8,
                        0xFF14141A, 0xFFECEFF4, 0xFF9AA0B4, 0xFF0F1014, 0xFF5A5F70, 0xFF00BCD4, 0xFFE6E8EF);
            case 2:
                return new BoardTheme(2, R.string.theme_green, true, 0xFF1B1209, 0xFF3E2A14, 0xFF1F6B3A, 0xFF14472A, 0xFFD8C79A,
                        0xFF3E2A14, 0xFFFFFFFF, 0xFFB5B5B5, 0xFF1A1A1A, 0xFF000000, 0xFFFFEB3B, 0xFFFFFFFF);
            case 3:
                return new BoardTheme(3, R.string.theme_blue, true, 0xFF07182E, 0xFF0D2B52, 0xFF1565C0, 0xFF0B3D91, 0xFF90CAF9,
                        0xFF0D2B52, 0xFFFFFFFF, 0xFFB0BEC5, 0xFF0A1929, 0xFF000000, 0xFFFF9800, 0xFFFFFFFF);
            case 4:
                return new BoardTheme(4, R.string.theme_gold, true, 0xFF000000, 0xFF0B0B0B, 0xFF1A1A1A, 0xFFB8860B, 0xFF3A3A3A,
                        0xFF000000, 0xFFF5E6B8, 0xFFB8860B, 0xFF050505, 0xFFB8860B, 0xFFFFD700, 0xFFF5E6B8);
            default:
                return new BoardTheme(0, R.string.theme_classic, false, 0xFF2A1A0C, 0xFF5D3A1A, 0xFFD9B27C, 0xFF8B4A24, 0xFFF1D9A8,
                        0xFF4A2C12, 0xFFF7F2E8, 0xFFB8AE98, 0xFF2B2420, 0xFF000000, 0xFFFFC107, 0xFFFFFFFF);
        }
    }
}
