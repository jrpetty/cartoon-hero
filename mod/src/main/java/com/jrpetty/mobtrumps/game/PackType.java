package com.jrpetty.mobtrumps.game;

import java.util.List;

/**
 * The kinds of booster pack. Standard pulls from every mob; the themed packs
 * pull from a curated pool and guarantee at least one high-rarity card.
 */
public enum PackType {

    STANDARD(0.09f, 0.0, (String[]) null),

    // themed pools, odds tilted toward the rarer cards (never guaranteed):
    // a Nether pack ~reliably gives rare Nether mobs, ~39% shot at the Wither;
    // a Boss pack is stacked with tough mobs, ~52% shot at a true legendary.
    NETHER(0.12f, 0.40,
            "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
            "strider", "wither", "wither_skeleton", "zoglin", "zombified_piglin"),

    BOSS(0.15f, 0.20,
            "warden", "wither", "ender_dragon", "elder_guardian", "evoker",
            "ravager", "vindicator", "guardian", "piglin_brute", "zoglin", "creaking",
            "breeze", "panda", "polar_bear", "blaze", "iron_golem", "pillager", "witch",
            "enderman", "wither_skeleton", "ghast", "hoglin", "stray", "husk", "bogged");

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
