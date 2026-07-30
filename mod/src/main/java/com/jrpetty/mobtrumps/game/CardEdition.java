package com.jrpetty.mobtrumps.game;

import java.util.Locale;

/**
 * What kind of print a card is. Edition changes artwork, effects and how much
 * collectors want it — it never changes a single Top Trumps statistic, so a
 * Trophy Creeper and a Standard Creeper play exactly the same.
 *
 * <p>Serials run in creation order across every edition: a Trophy does not get
 * its own separate 000001.
 */
public enum CardEdition {
    STANDARD("Standard", "", 0xFF6E6154),
    FOIL("Holographic", "✦", 0xFF8746C9),
    TROPHY("Trophy", "🏆", 0xFFD9B45B),
    FIRST_KILL("First Kill", "★", 0xFF1C7FA8),
    FLAWLESS("Flawless", "◆", 0xFF1E7A32);

    private final String label;
    private final String symbol;
    private final int color;

    CardEdition(String label, String symbol, int color) {
        this.label = label;
        this.symbol = symbol;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** A short glyph shown on the card face, or empty for a standard print. */
    public String symbol() {
        return symbol;
    }

    public int color() {
        return color;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parse an edition by name, falling back to STANDARD for anything unknown. */
    public static CardEdition byName(String name) {
        if (name != null) {
            for (CardEdition e : values()) {
                if (e.name().equalsIgnoreCase(name)) {
                    return e;
                }
            }
        }
        return STANDARD;
    }
}
