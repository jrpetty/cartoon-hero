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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("packs");
        CARDS_PER_PACK = b.comment("How many cards a legacy Mob Card Pack yields when opened.")
                .defineInRange("cardsPerPack", 5, 1, 9);
        FOIL_MULTIPLIER = b.comment("Multiplier applied to a legacy pack's holographic-foil chance (1.0 = default).")
                .defineInRange("foilMultiplier", 1.0, 0.0, 10.0);
        b.pop();

        b.push("deck");
        DECK_MAX = b.comment("Maximum cards in a custom battle deck.")
                .defineInRange("maxDeckSize", 16, 4, 40);
        b.pop();

        SPEC = b.build();
    }

    private Config() {
    }
}
