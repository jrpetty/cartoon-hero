package com.jrpetty.mobtrumps.game;

import java.util.Locale;

public enum Stat {
    HEALTH("Health", "HP"),
    ATTACK("Attack", "ATK"),
    SIZE("Size", "SIZE"),
    SPEED("Speed", "SPD"),
    FARMABLE("Farmable", "FARM"),
    RARITY("Rarity", "RARE");

    public final String label;
    public final String shortLabel;

    Stat(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
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
