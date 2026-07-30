package com.jrpetty.mobtrumps.game;

/**
 * How worn a physical card is, on a 0-100 scale. The number is authoritative;
 * the label is derived from it. A card starts Mint and only ever goes down —
 * nothing in normal play restores it, and a sleeve preserves what is left
 * rather than repairing what is gone.
 */
public final class CardCondition {

    public static final int MINT = 100;
    /** Points lost each time an unsleeved card enters a hand, after the free first. */
    public static final int DEFAULT_WEAR = 5;

    private CardCondition() {
    }

    /** The label for a condition percentage, e.g. 95 -> "Near Mint". */
    public static String label(int condition) {
        if (condition >= 100) return "Mint";
        if (condition >= 90) return "Near Mint";
        if (condition >= 75) return "Excellent";
        if (condition >= 55) return "Good";
        if (condition >= 30) return "Worn";
        if (condition >= 5) return "Damaged";
        return "Ruined";
    }

    /** A colour for the label, dark enough to read on a cream card face. */
    public static int color(int condition) {
        if (condition >= 100) return 0xFF1E7A32;
        if (condition >= 90) return 0xFF3D8B3D;
        if (condition >= 75) return 0xFF1C7FA8;
        if (condition >= 55) return 0xFF8A7A1C;
        if (condition >= 30) return 0xFFB4762A;
        if (condition >= 5) return 0xFFA33A25;
        return 0xFF6E2318;
    }

    /**
     * Wear stage 0-5, for picking how much damage to draw: 0 is untouched,
     * 5 is ruined. Purely presentational — the percentage is the truth.
     */
    public static int stage(int condition) {
        if (condition >= 100) return 0;
        if (condition >= 75) return 1;
        if (condition >= 55) return 2;
        if (condition >= 30) return 3;
        if (condition >= 5) return 4;
        return 5;
    }

    public static int clamp(int condition) {
        return Math.max(0, Math.min(100, condition));
    }
}
