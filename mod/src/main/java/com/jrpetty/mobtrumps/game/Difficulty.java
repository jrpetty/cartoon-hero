package com.jrpetty.mobtrumps.game;

import java.util.Locale;

/** How cleverly the CPU picks stats in a solo battle. */
public enum Difficulty {
    /** Picks a random stat — makes mistakes. */
    EASY,
    /** Leads with its card's highest raw stat. */
    NORMAL,
    /** Picks the stat with the best odds of winning, and mixes it up so it can't be read. */
    HARD;

    public String label() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static Difficulty byKey(String key) {
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(key)) return d;
        }
        return null;
    }
}
