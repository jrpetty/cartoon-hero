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
    public static final ModConfigSpec.IntValue SEASON_DAYS;
    public static final ModConfigSpec.BooleanValue CATEGORY_REWARDS;
    public static final ModConfigSpec.DoubleValue CATEGORY_REWARD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CARD_DROP_MULTIPLIER;
    public static final ModConfigSpec.IntValue CARD_WEAR_PER_HANDLING;
    public static final ModConfigSpec.IntValue SERIAL_DIGITS;
    public static final ModConfigSpec.BooleanValue RUINED_CARDS_PLAYABLE;
    public static final ModConfigSpec.IntValue GAME_XP;

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

        b.push("cards");
        CARD_WEAR_PER_HANDLING = b.comment("Condition an UNSLEEVED card loses per wear step.",
                        "A card gets 2 completely free handlings, and after that it takes",
                        "2 more handlings to lose one step -- so at the default 5 a card is",
                        "still Mint after 3 pickups, 95% after 4, and reaches 50% at 22.",
                        "Set to 0 to switch wear off entirely.")
                .defineInRange("wearPerHandling", 5, 0, 100);
        SERIAL_DIGITS = b.comment("Digits a per-mob serial is padded to, e.g. 6 -> CREEPER-000001.")
                .defineInRange("serialDigits", 6, 1, 10);
        RUINED_CARDS_PLAYABLE = b.comment("Whether a 0% (Ruined) card can still be played,",
                        "or becomes a display-only collectable.")
                .define("ruinedCardsPlayable", true);
        b.pop();

        b.push("rewards");
        GAME_XP = b.comment("Experience points paid to every player who finishes a game at the",
                        "dueling table -- win, lose or draw, in every mode. A zombie drops 5, so",
                        "the default 25 is about five zombies' worth. A game shorter than 15",
                        "seconds pays its share of that, so a quick hand cannot be farmed, and",
                        "leaving or forfeiting earns nothing. 0 switches it off.")
                .defineInRange("xpPerGame", 25, 0, 1000);
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
