package com.voxelia.mmo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config (XP rate, HUD toggle). Lives in config/voxelia_mmo-common.toml. */
public final class VoxeliaConfig {
    private VoxeliaConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue SHOW_HUD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Voxelia MMO settings").push("general");
        XP_MULTIPLIER = b
            .comment("Global multiplier applied to all skill XP gains.")
            .defineInRange("xpMultiplier", 1.0, 0.1, 100.0);
        SHOW_HUD = b
            .comment("Show the skills HUD overlay in the top-left corner.")
            .define("showHud", true);
        b.pop();
        SPEC = b.build();
    }

    public static double xpMultiplier() { return XP_MULTIPLIER.get(); }

    public static boolean showHud() { return SHOW_HUD.get(); }
}
