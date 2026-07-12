package com.jrpetty.mobtrumps.game;

import java.util.List;

/**
 * The kinds of booster pack. Standard pulls from every mob; the themed packs
 * pull from a curated pool and guarantee at least one high-rarity card.
 */
public enum PackType {

    STANDARD(0.09f, null, (String[]) null),

    NETHER(0.12f, Tier.RARE,
            "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
            "strider", "wither", "wither_skeleton", "zoglin", "zombified_piglin"),

    BOSS(0.15f, Tier.EPIC,
            "warden", "wither", "ender_dragon", "elder_guardian", "ravager", "iron_golem",
            "ghast", "hoglin", "evoker", "piglin_brute", "guardian", "blaze",
            "enderman", "wither_skeleton", "zoglin");

    public final float foilChance;
    public final Tier guarantee;
    private final String[] poolIds;

    PackType(float foilChance, Tier guarantee, String... poolIds) {
        this.foilChance = foilChance;
        this.guarantee = guarantee;
        this.poolIds = poolIds;
    }

    /** The mobs this pack can pull, or every mob for the standard pack. */
    public List<MobCard> pool() {
        return poolIds == null ? MobCards.ALL : MobCards.byIds(poolIds);
    }
}
