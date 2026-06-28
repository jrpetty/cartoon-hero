package com.voxelia.mmo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (server-authoritative) config: XP rate, per-level reward coefficients,
 * milestone perks, and the original six skills' active-ability tuning. The newer
 * skills (Excavation, Defense, Cooking, Alchemy, Archery) are passive.
 */
public final class VoxeliaConfig {
    private VoxeliaConfig() {}

    public static final ModConfigSpec SPEC;

    // rewards
    private static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue COMBAT_DAMAGE;
    private static final ModConfigSpec.DoubleValue FARMING_HEALTH;
    private static final ModConfigSpec.DoubleValue MINING_SPEED;
    private static final ModConfigSpec.DoubleValue FORAGING_SPEED;
    private static final ModConfigSpec.DoubleValue EXCAV_SPEED;
    private static final ModConfigSpec.DoubleValue MINING_FORTUNE;
    private static final ModConfigSpec.DoubleValue FORAGING_FORTUNE;
    private static final ModConfigSpec.DoubleValue EXCAV_FORTUNE;
    private static final ModConfigSpec.DoubleValue ACRO_DODGE;
    private static final ModConfigSpec.DoubleValue FISHING_LUCK_MAX;
    private static final ModConfigSpec.DoubleValue FISHING_SPEED_MAX;
    private static final ModConfigSpec.DoubleValue DEF_ARMOR;
    private static final ModConfigSpec.DoubleValue DEF_TOUGH;
    private static final ModConfigSpec.DoubleValue DEF_KB;
    private static final ModConfigSpec.DoubleValue ALCH_DURATION;
    private static final ModConfigSpec.DoubleValue ARCHERY_POWER;

    // perks
    private static final ModConfigSpec.DoubleValue COMBAT_LIFESTEAL;
    private static final ModConfigSpec.DoubleValue ACRO_FALL_REDUCTION;
    private static final ModConfigSpec.DoubleValue FISHING_TREASURE_MAX;
    private static final ModConfigSpec.IntValue MINING_HASTE_LEVEL;
    private static final ModConfigSpec.IntValue TELEKINESIS_LEVEL;
    private static final ModConfigSpec.IntValue LAST_STAND_LEVEL;
    private static final ModConfigSpec.IntValue COOKING_FEAST_LEVEL;
    private static final ModConfigSpec.DoubleValue DEATH_XP_LOSS;
    private static final ModConfigSpec.IntValue MASTERY_KILLS_PER_TIER;
    private static final ModConfigSpec.IntValue MASTERY_MAX_TIER;
    private static final ModConfigSpec.DoubleValue MASTERY_DAMAGE_PER_TIER;
    private static final ModConfigSpec.DoubleValue MASTERY_LOOT_PER_TIER;
    private static final ModConfigSpec.BooleanValue SHOW_CHAT_TITLE;
    private static final ModConfigSpec.IntValue TALENT_MAX_RANK;
    private static final ModConfigSpec.IntValue TALENT_POINTS_PER_LEVEL;
    private static final ModConfigSpec.DoubleValue PRODIGY_XP_PER_RANK;
    private static final ModConfigSpec.DoubleValue TALENT_MASTERY_SCALE;

    // active abilities (original six)
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

        b.comment("XP and per-level reward tuning (max level 100).").push("rewards");
        XP_MULTIPLIER = b.comment("Global multiplier applied to all skill XP gains.")
            .defineInRange("xpMultiplier", 1.0, 0.0, 1000.0);
        COMBAT_DAMAGE = b.comment("Combat: bonus attack damage per level.")
            .defineInRange("combatDamagePerLevel", 0.1, 0.0, 100.0);
        FARMING_HEALTH = b.comment("Farming: bonus max health (HP) per level.")
            .defineInRange("farmingHealthPerLevel", 0.2, 0.0, 100.0);
        MINING_SPEED = b.comment("Mining: block-break-speed bonus per level (fraction).")
            .defineInRange("miningSpeedPerLevel", 0.005, 0.0, 10.0);
        FORAGING_SPEED = b.comment("Foraging: block-break-speed bonus per level (fraction).")
            .defineInRange("foragingSpeedPerLevel", 0.005, 0.0, 10.0);
        EXCAV_SPEED = b.comment("Excavation: block-break-speed bonus per level (fraction).")
            .defineInRange("excavationSpeedPerLevel", 0.005, 0.0, 10.0);
        MINING_FORTUNE = b.comment("Mining: Fortune factor per level on ores (no Silk Touch).")
            .defineInRange("miningFortunePerLevel", 0.01, 0.0, 10.0);
        FORAGING_FORTUNE = b.comment("Foraging: Fortune factor per level on logs/leaves (no Silk Touch).")
            .defineInRange("foragingFortunePerLevel", 0.01, 0.0, 10.0);
        EXCAV_FORTUNE = b.comment("Excavation: Fortune factor per level on shovel blocks (no Silk Touch).")
            .defineInRange("excavationFortunePerLevel", 0.01, 0.0, 10.0);
        ACRO_DODGE = b.comment("Acrobatics: dodge chance per level (0.006 => 60% at 100).")
            .defineInRange("acrobaticsDodgePerLevel", 0.006, 0.0, 0.01);
        FISHING_LUCK_MAX = b.comment("Fishing: max bonus luck (while fishing) at max level.")
            .defineInRange("fishingLuckMax", 4.0, 0.0, 100.0);
        FISHING_SPEED_MAX = b.comment("Fishing: max bite-speed multiplier at max level.")
            .defineInRange("fishingSpeedMax", 2.0, 1.0, 10.0);
        DEF_ARMOR = b.comment("Defense: bonus armor per level.")
            .defineInRange("defenseArmorPerLevel", 0.12, 0.0, 100.0);
        DEF_TOUGH = b.comment("Defense: bonus armor toughness per level.")
            .defineInRange("defenseToughnessPerLevel", 0.08, 0.0, 100.0);
        DEF_KB = b.comment("Defense: bonus knockback resistance per level.")
            .defineInRange("defenseKnockbackResistPerLevel", 0.003, 0.0, 0.01);
        ALCH_DURATION = b.comment("Alchemy: beneficial-effect duration extension per level on finishing a potion (0.005 => +50% at 100).")
            .defineInRange("alchemyDurationPerLevel", 0.005, 0.0, 1.0);
        ARCHERY_POWER = b.comment("Archery: Power Shot bonus damage per level on fully-drawn (critical) arrows (0.06 => +6 at 100).")
            .defineInRange("archeryPowerShotPerLevel", 0.06, 0.0, 10.0);
        b.pop();

        b.comment("Milestone & conditional perks.").push("perks");
        COMBAT_LIFESTEAL = b.comment("Combat: health healed on a kill, per level.")
            .defineInRange("combatLifeStealPerLevel", 0.05, 0.0, 10.0);
        ACRO_FALL_REDUCTION = b.comment("Acrobatics: fall damage reduced per level.")
            .defineInRange("acrobaticsFallReductionPerLevel", 0.009, 0.0, 0.01);
        FISHING_TREASURE_MAX = b.comment("Fishing: max treasure-bonus chance per catch at max level.")
            .defineInRange("fishingTreasureChanceMax", 0.5, 0.0, 1.0);
        MINING_HASTE_LEVEL = b.comment("Mining: level at which holding a pickaxe grants Haste (0 disables).")
            .defineInRange("miningHasteLevel", 25, 0, 100);
        TELEKINESIS_LEVEL = b.comment("Mining: level at which mined drops go to your inventory (0 disables).")
            .defineInRange("telekinesisLevel", 100, 0, 100);
        LAST_STAND_LEVEL = b.comment("Defense: level that unlocks Last Stand — Resistance while below 35% health (0 disables).")
            .defineInRange("lastStandLevel", 20, 0, 100);
        COOKING_FEAST_LEVEL = b.comment("Cooking: level that unlocks Well Fed — a short regeneration after eating (0 disables).")
            .defineInRange("cookingWellFedLevel", 20, 0, 100);
        DEATH_XP_LOSS = b.comment("Fraction of EVERY skill's XP lost on death (0.0 = keep everything, 0.1 = lose 10%, can drop levels).")
            .defineInRange("deathXpLossPercent", 0.05, 0.0, 1.0);
        b.pop();

        b.comment("Mob Mastery / Bestiary — repeatedly killing a mob type grants a bonus vs that type.").push("mastery");
        MASTERY_KILLS_PER_TIER = b.comment("Kills of a mob type needed per mastery tier.")
            .defineInRange("killsPerTier", 50, 1, 100000);
        MASTERY_MAX_TIER = b.comment("Maximum mastery tier per mob type.")
            .defineInRange("maxTier", 10, 0, 1000);
        MASTERY_DAMAGE_PER_TIER = b.comment("Bonus damage vs a mastered type per tier (0.02 = +2%/tier).")
            .defineInRange("damagePerTier", 0.02, 0.0, 10.0);
        MASTERY_LOOT_PER_TIER = b.comment("Chance per tier to double a drop from a mastered type (0.03 = +3%/tier).")
            .defineInRange("lootChancePerTier", 0.03, 0.0, 1.0);
        b.pop();

        b.comment("Social / display.").push("social");
        SHOW_CHAT_TITLE = b.comment("Prefix chat with the player's level + title, e.g. [Lv 42 • Master Miner].")
            .define("showChatTitle", true);
        b.pop();

        b.comment("Talent tree — spend points (earned per skill level) on Prodigy (XP) or Mastery (power).").push("talents");
        TALENT_POINTS_PER_LEVEL = b.comment("Talent points earned per skill level.")
            .defineInRange("pointsPerLevel", 1, 0, 100);
        TALENT_MAX_RANK = b.comment("Maximum rank per talent.")
            .defineInRange("maxRank", 5, 1, 100);
        PRODIGY_XP_PER_RANK = b.comment("Prodigy: extra XP gain per rank (0.08 = +8%/rank).")
            .defineInRange("prodigyXpPerRank", 0.08, 0.0, 10.0);
        TALENT_MASTERY_SCALE = b.comment("Global multiplier on all Mastery bonuses (tune the whole branch at once).")
            .defineInRange("masteryScale", 1.0, 0.0, 100.0);
        b.pop();

        b.comment("Active abilities for the original six skills (cooldown-balanced).").push("abilities");
        FRENZY_LEVEL = b.comment("Combat: Frenzy unlock level (0 disables).").defineInRange("frenzyLevel", 20, 0, 100);
        FRENZY_COOLDOWN = b.comment("Frenzy cooldown (seconds).").defineInRange("frenzyCooldownSeconds", 50, 1, 3600);
        LEAP_LEVEL = b.comment("Acrobatics: Leap unlock level (0 disables).").defineInRange("leapLevel", 15, 0, 100);
        LEAP_COOLDOWN = b.comment("Leap cooldown (seconds).").defineInRange("leapCooldownSeconds", 6, 1, 3600);
        FOCUS_LEVEL = b.comment("Mining: Miner's Focus unlock level (0 disables).").defineInRange("minersFocusLevel", 20, 0, 100);
        FOCUS_COOLDOWN = b.comment("Miner's Focus cooldown (seconds).").defineInRange("minersFocusCooldownSeconds", 60, 1, 3600);
        OVERGROWTH_LEVEL = b.comment("Foraging: Overgrowth unlock level (0 disables).").defineInRange("overgrowthLevel", 25, 0, 100);
        OVERGROWTH_COOLDOWN = b.comment("Overgrowth cooldown (seconds).").defineInRange("overgrowthCooldownSeconds", 45, 1, 3600);
        MEAL_LEVEL = b.comment("Farming: Hearty Meal unlock level (0 disables).").defineInRange("heartyMealLevel", 20, 0, 100);
        MEAL_COOLDOWN = b.comment("Hearty Meal cooldown (seconds).").defineInRange("heartyMealCooldownSeconds", 60, 1, 3600);
        REEL_LEVEL = b.comment("Fishing: Reel unlock level (0 disables).").defineInRange("reelLevel", 15, 0, 100);
        REEL_COOLDOWN = b.comment("Reel cooldown (seconds).").defineInRange("reelCooldownSeconds", 8, 1, 3600);
        b.pop();

        SPEC = b.build();
    }

    public static double xpMultiplier()        { return XP_MULTIPLIER.get(); }
    public static double combatDamagePerLevel(){ return COMBAT_DAMAGE.get(); }
    public static double farmingHealthPerLevel(){ return FARMING_HEALTH.get(); }
    public static double miningSpeedPerLevel() { return MINING_SPEED.get(); }
    public static double foragingSpeedPerLevel(){ return FORAGING_SPEED.get(); }
    public static double excavationSpeedPerLevel(){ return EXCAV_SPEED.get(); }
    public static double miningFortunePerLevel(){ return MINING_FORTUNE.get(); }
    public static double foragingFortunePerLevel(){ return FORAGING_FORTUNE.get(); }
    public static double excavationFortunePerLevel(){ return EXCAV_FORTUNE.get(); }
    public static double acrobaticsDodgePerLevel(){ return ACRO_DODGE.get(); }
    public static double fishingLuckMax()      { return FISHING_LUCK_MAX.get(); }
    public static double fishingSpeedMax()     { return FISHING_SPEED_MAX.get(); }
    public static double defenseArmorPerLevel(){ return DEF_ARMOR.get(); }
    public static double defenseToughnessPerLevel(){ return DEF_TOUGH.get(); }
    public static double defenseKnockbackResistPerLevel(){ return DEF_KB.get(); }
    public static double alchemyDurationPerLevel(){ return ALCH_DURATION.get(); }
    public static double archeryPowerShotPerLevel(){ return ARCHERY_POWER.get(); }

    public static double combatLifeStealPerLevel() { return COMBAT_LIFESTEAL.get(); }
    public static double acrobaticsFallReductionPerLevel() { return ACRO_FALL_REDUCTION.get(); }
    public static double fishingTreasureChanceMax() { return FISHING_TREASURE_MAX.get(); }
    public static int miningHasteLevel()       { return MINING_HASTE_LEVEL.get(); }
    public static int telekinesisLevel()       { return TELEKINESIS_LEVEL.get(); }
    public static int lastStandLevel()         { return LAST_STAND_LEVEL.get(); }
    public static int cookingWellFedLevel()    { return COOKING_FEAST_LEVEL.get(); }
    public static double deathXpLossPercent()  { return DEATH_XP_LOSS.get(); }
    public static int masteryKillsPerTier()    { return MASTERY_KILLS_PER_TIER.get(); }
    public static int masteryMaxTier()         { return MASTERY_MAX_TIER.get(); }
    public static double masteryDamagePerTier(){ return MASTERY_DAMAGE_PER_TIER.get(); }
    public static double masteryLootChancePerTier(){ return MASTERY_LOOT_PER_TIER.get(); }
    public static boolean showChatTitle()      { return SHOW_CHAT_TITLE.get(); }
    public static int talentMaxRank()          { return TALENT_MAX_RANK.get(); }
    public static int talentPointsPerLevel()   { return TALENT_POINTS_PER_LEVEL.get(); }
    public static double prodigyXpPerRank()    { return PRODIGY_XP_PER_RANK.get(); }
    public static double talentMasteryScale()  { return TALENT_MASTERY_SCALE.get(); }

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
