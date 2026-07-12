package com.jrpetty.mobtrumps.game;

import java.util.List;

/**
 * The kinds of booster pack. Standard pulls from every mob; the themed packs
 * pull from a curated pool and guarantee at least one high-rarity card.
 */
public enum PackType {

    STANDARD(0.09f, 0.0, (String[]) null),

    // Themed pools drawn at normal spawn odds (bias 0). The packs are special
    // because their pool has no common filler, not because they force rares —
    // legendaries stay uncommon. Measured: Nether ~10% shot at the Wither;
    // Boss ~20% at a true legendary (vs ~15% from a standard pack).
    NETHER(0.12f, 0.0,
            "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
            "strider", "wither", "wither_skeleton", "zoglin", "zombified_piglin"),

    BOSS(0.15f, 0.0,
            "warden", "wither", "ender_dragon", "elder_guardian",
            "ravager", "vindicator", "guardian", "piglin_brute", "zoglin", "creaking",
            "breeze", "panda", "polar_bear", "blaze", "iron_golem", "pillager", "witch",
            "enderman", "wither_skeleton", "ghast", "stray", "bogged", "hoglin", "husk");

    public final float foilChance;
    /** 0 = normal odds, 1 = fully favours the rarest cards in the pool. */
    public final double bias;
    private final String[] poolIds;

    PackType(float foilChance, double bias, String... poolIds) {
        this.foilChance = foilChance;
        this.bias = bias;
        this.poolIds = poolIds;
    }

    /** The mobs this pack can pull, or every mob for the standard pack. */
    public List<MobCard> pool() {
        return poolIds == null ? MobCards.ALL : MobCards.byIds(poolIds);
    }
}
