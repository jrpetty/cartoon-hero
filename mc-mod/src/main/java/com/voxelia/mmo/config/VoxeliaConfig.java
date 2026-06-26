package com.voxelia.mmo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (server-authoritative) config: XP rate and every per-level reward
 * coefficient, so balance is fully tunable without editing code.
 * Lives in config/voxelia_mmo-common.toml.
 */
public final class VoxeliaConfig {
    private VoxeliaConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue COMBAT_DAMAGE;
    private static final ModConfigSpec.DoubleValue FARMING_HEALTH;
    private static final ModConfigSpec.DoubleValue MINING_SPEED;
    private static final ModConfigSpec.DoubleValue FORAGING_SPEED;
    private static final ModConfigSpec.DoubleValue MINING_FORTUNE;
    private static final ModConfigSpec.DoubleValue FORAGING_FORTUNE;
    private static final ModConfigSpec.DoubleValue ACRO_DODGE;
    private static final ModConfigSpec.DoubleValue FISHING_LUCK_MAX;
    private static final ModConfigSpec.DoubleValue FISHING_SPEED_MAX;
    private static final ModConfigSpec.DoubleValue COMBAT_LIFESTEAL;
    private static final ModConfigSpec.DoubleValue ACRO_FALL_REDUCTION;
    private static final ModConfigSpec.DoubleValue FISHING_TREASURE_MAX;
    private static final ModConfigSpec.IntValue MINING_HASTE_LEVEL;
    private static final ModConfigSpec.IntValue TELEKINESIS_LEVEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Voxelia MMO — XP and per-level reward tuning (max level 100).").push("rewards");

        XP_MULTIPLIER = b.comment("Global multiplier applied to all skill XP gains.")
            .defineInRange("xpMultiplier", 1.0, 0.0, 1000.0);
        COMBAT_DAMAGE = b.comment("Combat: bonus attack damage per level (0.1 => +~10 at level 100).")
            .defineInRange("combatDamagePerLevel", 0.1, 0.0, 100.0);
        FARMING_HEALTH = b.comment("Farming: bonus max health (HP) per level (0.2 => +1 heart per 5 levels, ~doubles HP at 100).")
            .defineInRange("farmingHealthPerLevel", 0.2, 0.0, 100.0);
        MINING_SPEED = b.comment("Mining: block-break-speed bonus per level as a fraction (0.005 => +0.5%/level).")
            .defineInRange("miningSpeedPerLevel", 0.005, 0.0, 10.0);
        FORAGING_SPEED = b.comment("Foraging: block-break-speed bonus per level as a fraction.")
            .defineInRange("foragingSpeedPerLevel", 0.005, 0.0, 10.0);
        MINING_FORTUNE = b.comment("Mining: Fortune bonus-drop factor per level on ores (0.01 => +1%/level, ~2x at 100). Never applies with Silk Touch.")
            .defineInRange("miningFortunePerLevel", 0.01, 0.0, 10.0);
        FORAGING_FORTUNE = b.comment("Foraging: Fortune bonus-drop factor per level on logs/leaves. Never applies with Silk Touch.")
            .defineInRange("foragingFortunePerLevel", 0.01, 0.0, 10.0);
        ACRO_DODGE = b.comment("Acrobatics: dodge chance per level (0.006 => +0.6%/level, 60% at level 100).")
            .defineInRange("acrobaticsDodgePerLevel", 0.006, 0.0, 0.01);
        FISHING_LUCK_MAX = b.comment("Fishing: maximum bonus luck (applied only while a line is in the water) at max level.")
            .defineInRange("fishingLuckMax", 4.0, 0.0, 100.0);
        FISHING_SPEED_MAX = b.comment("Fishing: maximum bite-speed multiplier at max level (2.0 => twice as fast).")
            .defineInRange("fishingSpeedMax", 2.0, 1.0, 10.0);
        b.pop();

        b.comment("Milestone perks.").push("perks");
        COMBAT_LIFESTEAL = b.comment("Combat: health healed on a kill, per Combat level (0.05 => +5 HP at level 100).")
            .defineInRange("combatLifeStealPerLevel", 0.05, 0.0, 10.0);
        ACRO_FALL_REDUCTION = b.comment("Acrobatics: fall damage reduced per level (0.009 => ~90% softer landings at level 100). XP is still earned on the full fall.")
            .defineInRange("acrobaticsFallReductionPerLevel", 0.009, 0.0, 0.01);
        FISHING_TREASURE_MAX = b.comment("Fishing: maximum chance of a treasure bonus on a catch, at max level.")
            .defineInRange("fishingTreasureChanceMax", 0.5, 0.0, 1.0);
        MINING_HASTE_LEVEL = b.comment("Mining: level at which holding a pickaxe grants Haste (0 disables).")
            .defineInRange("miningHasteLevel", 25, 0, 100);
        TELEKINESIS_LEVEL = b.comment("Mining: level at which mined drops go straight to your inventory (0 disables).")
            .defineInRange("telekinesisLevel", 100, 0, 100);
        b.pop();

        SPEC = b.build();
    }

    public static double xpMultiplier()        { return XP_MULTIPLIER.get(); }
    public static double combatDamagePerLevel(){ return COMBAT_DAMAGE.get(); }
    public static double farmingHealthPerLevel(){ return FARMING_HEALTH.get(); }
    public static double miningSpeedPerLevel() { return MINING_SPEED.get(); }
    public static double foragingSpeedPerLevel(){ return FORAGING_SPEED.get(); }
    public static double miningFortunePerLevel(){ return MINING_FORTUNE.get(); }
    public static double foragingFortunePerLevel(){ return FORAGING_FORTUNE.get(); }
    public static double acrobaticsDodgePerLevel(){ return ACRO_DODGE.get(); }
    public static double fishingLuckMax()      { return FISHING_LUCK_MAX.get(); }
    public static double fishingSpeedMax()     { return FISHING_SPEED_MAX.get(); }
    public static double combatLifeStealPerLevel() { return COMBAT_LIFESTEAL.get(); }
    public static double acrobaticsFallReductionPerLevel() { return ACRO_FALL_REDUCTION.get(); }
    public static double fishingTreasureChanceMax() { return FISHING_TREASURE_MAX.get(); }
    public static int miningHasteLevel()       { return MINING_HASTE_LEVEL.get(); }
    public static int telekinesisLevel()       { return TELEKINESIS_LEVEL.get(); }
}
