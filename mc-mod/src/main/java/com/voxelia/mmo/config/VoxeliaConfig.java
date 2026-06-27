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
    private static final ModConfigSpec.IntValue FRENZY_LEVEL;
    private static final ModConfigSpec.IntValue FRENZY_COOLDOWN;
    private static final ModConfigSpec.IntValue LEAP_LEVEL;
    private static final ModConfigSpec.IntValue LEAP_COOLDOWN;
    private static final ModConfigSpec.IntValue FOCUS_LEVEL;
    private static final ModConfigSpec.IntValue FOCUS_COOLDOWN;
    private static final ModConfigSpec.IntValue OVERGROWTH_LEVEL;
    private static final ModConfigSpec.IntValue OVERGROWTH_COOLDOWN;
    private static final ModConfigSpec.IntValue MEAL_LEVEL;
    private static final ModConfigSpec.IntValue MEAL_COOLDOWN;
    private static final ModConfigSpec.IntValue REEL_LEVEL;
    private static final ModConfigSpec.IntValue REEL_COOLDOWN;

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

        b.comment("Active abilities (keybinds, balanced by cooldowns).").push("abilities");
        FRENZY_LEVEL = b.comment("Combat level that unlocks Frenzy (a short Strength + Speed burst). 0 disables.")
            .defineInRange("frenzyLevel", 20, 0, 100);
        FRENZY_COOLDOWN = b.comment("Frenzy cooldown in seconds.")
            .defineInRange("frenzyCooldownSeconds", 50, 1, 3600);
        LEAP_LEVEL = b.comment("Acrobatics level that unlocks Leap (a forward/upward dash with a safe landing). 0 disables.")
            .defineInRange("leapLevel", 15, 0, 100);
        LEAP_COOLDOWN = b.comment("Leap cooldown in seconds.")
            .defineInRange("leapCooldownSeconds", 6, 1, 3600);
        FOCUS_LEVEL = b.comment("Mining level that unlocks Miner's Focus (Haste + Night Vision). 0 disables.")
            .defineInRange("minersFocusLevel", 20, 0, 100);
        FOCUS_COOLDOWN = b.comment("Miner's Focus cooldown in seconds.")
            .defineInRange("minersFocusCooldownSeconds", 60, 1, 3600);
        OVERGROWTH_LEVEL = b.comment("Foraging level that unlocks Overgrowth (bonemeals nearby plants). 0 disables.")
            .defineInRange("overgrowthLevel", 25, 0, 100);
        OVERGROWTH_COOLDOWN = b.comment("Overgrowth cooldown in seconds.")
            .defineInRange("overgrowthCooldownSeconds", 45, 1, 3600);
        MEAL_LEVEL = b.comment("Farming level that unlocks Hearty Meal (Regeneration + Saturation). 0 disables.")
            .defineInRange("heartyMealLevel", 20, 0, 100);
        MEAL_COOLDOWN = b.comment("Hearty Meal cooldown in seconds.")
            .defineInRange("heartyMealCooldownSeconds", 60, 1, 3600);
        REEL_LEVEL = b.comment("Fishing level that unlocks Reel (yanks your hooked target, or pulls you to the bobber). 0 disables.")
            .defineInRange("reelLevel", 15, 0, 100);
        REEL_COOLDOWN = b.comment("Reel cooldown in seconds.")
            .defineInRange("reelCooldownSeconds", 8, 1, 3600);
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
    public static int frenzyLevel()            { return FRENZY_LEVEL.get(); }
    public static int frenzyCooldownSeconds()  { return FRENZY_COOLDOWN.get(); }
    public static int leapLevel()              { return LEAP_LEVEL.get(); }
    public static int leapCooldownSeconds()    { return LEAP_COOLDOWN.get(); }
    public static int minersFocusLevel()       { return FOCUS_LEVEL.get(); }
    public static int minersFocusCooldownSeconds() { return FOCUS_COOLDOWN.get(); }
    public static int overgrowthLevel()        { return OVERGROWTH_LEVEL.get(); }
    public static int overgrowthCooldownSeconds() { return OVERGROWTH_COOLDOWN.get(); }
    public static int heartyMealLevel()        { return MEAL_LEVEL.get(); }
    public static int heartyMealCooldownSeconds() { return MEAL_COOLDOWN.get(); }
    public static int reelLevel()              { return REEL_LEVEL.get(); }
    public static int reelCooldownSeconds()    { return REEL_COOLDOWN.get(); }
}
