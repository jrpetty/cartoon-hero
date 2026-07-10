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
}
