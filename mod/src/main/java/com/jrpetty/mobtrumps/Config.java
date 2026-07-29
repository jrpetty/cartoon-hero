package com.jrpetty.mobtrumps;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server/common config so admins can tune the mod without editing code.
 * (Pack recipes stay data-driven — override them with a data pack.)
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // Legacy Mob Card Packs (no longer granted, but still openable if held) read these.
    public static final ModConfigSpec.IntValue CARDS_PER_PACK;
    public static final ModConfigSpec.DoubleValue FOIL_MULTIPLIER;
    public static final ModConfigSpec.IntValue DECK_MAX;
    public static final ModConfigSpec.IntValue SEASON_DAYS;
    public static final ModConfigSpec.BooleanValue CATEGORY_REWARDS;
    public static final ModConfigSpec.DoubleValue CATEGORY_REWARD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CARD_DROP_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("packs");
        CARDS_PER_PACK = b.comment("How many cards a legacy Mob Card Pack yields when opened.")
                .defineInRange("cardsPerPack", 5, 1, 9);
        FOIL_MULTIPLIER = b.comment("Multiplier applied to a legacy pack's holographic-foil chance (1.0 = default).")
                .defineInRange("foilMultiplier", 1.0, 0.0, 10.0);
        b.pop();

        b.push("drops");
        CARD_DROP_MULTIPLIER = b.comment("Scales the chance that killing a mob drops its card.",
                        "Base chances are by collector tier: common 1 in 20, uncommon 1 in 10,",
                        "rare 1 in 5, epic 1 in 2, legendary always (so every boss is certain).",
                        "2.0 doubles every chance; 20.0 effectively makes all cards guaranteed.")
                .defineInRange("cardDropMultiplier", 1.0, 0.0, 20.0);
        b.pop();

        b.push("deck");
        DECK_MAX = b.comment("Maximum cards in a custom battle deck.")
                .defineInRange("maxDeckSize", 16, 4, 40);
        b.pop();

        b.push("ranked");
        SEASON_DAYS = b.comment("Length of a ranked season in real days before ratings soft-reset.")
                .defineInRange("seasonDays", 7, 1, 90);
        b.pop();

        b.push("categories");
        CATEGORY_REWARDS = b.comment("Award a one-time loot haul (diamonds/iron/gold + a random",
                        "enchanted armour piece) for collecting every mob in a category.")
                .define("categoryRewardsEnabled", true);
        CATEGORY_REWARD_MULTIPLIER = b.comment("Scales the material amounts in every category reward",
                        "(1.0 = default; 2.0 = double the diamonds/iron/gold). Armour tier is unaffected.")
                .defineInRange("categoryRewardMultiplier", 1.0, 0.0, 10.0);
        b.pop();

        SPEC = b.build();
    }

    private Config() {
    }
}
