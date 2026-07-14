package com.jrpetty.mobtrumps.game;

import java.util.Locale;

/** Collector tier of a card, derived from how rarely the mob spawns. */
public enum Tier {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY;

    public String label() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /**
     * How many of this mob you must kill to unlock its holographic card.
     * Rarer mobs are harder to find, so they need fewer kills.
     */
    public int foilKillThreshold() {
        return switch (this) {
            case COMMON -> 100;
            case UNCOMMON -> 75;
            case RARE -> 25;
            case EPIC -> 10;
            case LEGENDARY -> 5;
        };
    }
}
