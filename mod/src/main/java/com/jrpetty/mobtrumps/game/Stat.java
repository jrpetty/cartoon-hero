package com.jrpetty.mobtrumps.game;

import java.util.Locale;

public enum Stat {
    HEALTH("Health", "HP", false),
    ATTACK("Attack", "ATK", false),
    SIZE("Size", "SIZE", false),
    SPEED("Speed", "SPD", false),
    FARMABLE("Farmable", "FARM", false),
    /** Spawn rarity: 1 = legendary, 10 = everywhere. The LOWER number wins. */
    RARITY("Rarity", "RARE", true);

    public final String label;
    public final String shortLabel;
    /** True when a lower number beats a higher one (Rarity — the harder mob to get). */
    public final boolean lowerWins;

    Stat(String label, String shortLabel, boolean lowerWins) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.lowerWins = lowerWins;
    }

    /**
     * The comparable score for a value on this stat: higher always wins. For a
     * {@link #lowerWins} stat the raw value is negated, so 1 beats 9.
     */
    public int score(int value) {
        return lowerWins ? -value : value;
    }

    /**
     * How strong a value is on a common 0-10 scale, regardless of direction.
     * Used to rank a card's stats against each other (a rarity-1 legendary is a
     * 9-strength Rarity card, not a 1).
     */
    public int strength(int value) {
        return lowerWins ? 10 - value : value;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive lookup, returns null for unknown stats. */
    public static Stat byKey(String key) {
        for (Stat s : values()) {
            if (s.key().equalsIgnoreCase(key)) return s;
        }
        return null;
    }
}
